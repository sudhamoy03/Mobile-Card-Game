import { Socket, Server } from 'socket.io';
import { roomService } from '../../services/room.service';
import { authService } from '../../services/auth.service';
import { toPublicRoom } from '../../types/room.types';
import { toPublicPlayer } from '../../types/player.types';
import {
  CreateRoomPayload,
  JoinRoomPayload,
  ReconnectPayload,
  AddBotPayload,
  RoomCreatedResponse,
  RoomJoinedResponse,
  PlayerJoinedEvent,
  PlayerReconnectedEvent
} from '../../types/events.types';
import { logger } from '../../utils/logger';

export function registerRoomHandlers(io: Server, socket: Socket): void {
  /**
   * Event: create_room
   * Player 1 creates a new room, receives 4-digit code, and joins socket channel
   */
  socket.on('create_room', async (payload: CreateRoomPayload, callback?: (res: any) => void) => {
    try {
      logger.info('Socket:Room', `create_room requested by ${payload.playerName}`);

      const user = await authService.verifyToken(payload.idToken, payload.playerName);
      const { room, player, sessionToken } = roomService.createRoom(
        user.name,
        user.uid,
        socket.id,
        payload.targetScore || 300
      );

      // Join Socket.IO room channel
      socket.join(room.roomCode);

      const response: RoomCreatedResponse = {
        roomCode: room.roomCode,
        player: toPublicPlayer(player),
        sessionToken,
        roomState: toPublicRoom(room)
      };

      socket.emit('room_created', response);
      if (callback) callback({ success: true, data: response });
    } catch (error: any) {
      logger.error('Socket:Room', 'create_room error', error);
      const errorResponse = { code: 'CREATE_ROOM_FAILED', message: error.message };
      socket.emit('error', errorResponse);
      if (callback) callback({ success: false, error: errorResponse });
    }
  });

  /**
   * Event: join_room
   * Player 2 enters 4-digit code, joins room, server broadcasts PLAYER_JOINED to everyone
   */
  socket.on('join_room', async (payload: JoinRoomPayload, callback?: (res: any) => void) => {
    try {
      const code = payload.roomCode?.trim();
      logger.info('Socket:Room', `join_room requested for code: ${code} by ${payload.playerName}`);

      if (!code || code.length !== 4) {
        throw new Error('Room code must be exactly 4 digits.');
      }

      const user = await authService.verifyToken(payload.idToken, payload.playerName);
      const { room, player, sessionToken } = roomService.joinRoom(
        code,
        user.name,
        user.uid,
        socket.id
      );

      // Join Socket.IO room channel
      socket.join(code);

      const joinedResponse: RoomJoinedResponse = {
        roomCode: room.roomCode,
        player: toPublicPlayer(player),
        sessionToken,
        roomState: toPublicRoom(room)
      };

      // Emit to the joining player
      socket.emit('room_joined', joinedResponse);

      // Broadcast to all other players in the room that a player joined
      const broadcastEvent: PlayerJoinedEvent = {
        player: toPublicPlayer(player),
        roomState: toPublicRoom(room)
      };
      socket.to(code).emit('player_joined', broadcastEvent);

      // Also broadcast updated room state
      io.in(code).emit('room_state_updated', { roomState: toPublicRoom(room) });

      if (callback) callback({ success: true, data: joinedResponse });
    } catch (error: any) {
      logger.error('Socket:Room', 'join_room error', error);
      const errorResponse = { code: 'JOIN_ROOM_FAILED', message: error.message };
      socket.emit('error', errorResponse);
      if (callback) callback({ success: false, error: errorResponse });
    }
  });

  /**
   * Event: reconnect_session
   * Player reconnects after network drop using their sessionToken
   */
  socket.on('reconnect_session', async (payload: ReconnectPayload, callback?: (res: any) => void) => {
    try {
      const code = payload.roomCode?.trim();
      logger.info('Socket:Room', `reconnect_session requested for room ${code}`);

      const { room, player } = roomService.reconnectPlayer(code, payload.sessionToken, socket.id);

      socket.join(code);

      const reconnectedEvent: PlayerReconnectedEvent = {
        player: toPublicPlayer(player),
        roomState: toPublicRoom(room)
      };

      socket.emit('session_restored', {
        roomCode: room.roomCode,
        player: toPublicPlayer(player),
        roomState: toPublicRoom(room)
      });

      // Broadcast player reconnection to room
      io.in(code).emit('player_reconnected', reconnectedEvent);
      io.in(code).emit('room_state_updated', { roomState: toPublicRoom(room) });

      if (callback) callback({ success: true, data: reconnectedEvent });
    } catch (error: any) {
      logger.error('Socket:Room', 'reconnect_session error', error);
      const errorResponse = { code: 'RECONNECT_FAILED', message: error.message };
      socket.emit('error', errorResponse);
      if (callback) callback({ success: false, error: errorResponse });
    }
  });

  /**
   * Event: add_bot
   * Host adds an AI bot to fill an empty seat
   */
  socket.on('add_bot', (payload: AddBotPayload, callback?: (res: any) => void) => {
    try {
      if (roomService.isActionProcessed(payload.actionId)) {
        logger.warn('Socket:Room', `Duplicate add_bot action ignored: ${payload.actionId}`);
        if (callback) callback({ success: true, duplicate: true });
        return;
      }
      roomService.markActionProcessed(payload.actionId);

      const { room, botPlayer } = roomService.addBot(payload.roomCode, socket.id);

      io.in(payload.roomCode).emit('player_joined', {
        player: toPublicPlayer(botPlayer),
        roomState: toPublicRoom(room)
      });

      io.in(payload.roomCode).emit('room_state_updated', { roomState: toPublicRoom(room) });

      if (callback) callback({ success: true });
    } catch (error: any) {
      logger.error('Socket:Room', 'add_bot error', error);
      const errorResponse = { code: 'ADD_BOT_FAILED', message: error.message, actionId: payload.actionId };
      socket.emit('error', errorResponse);
      if (callback) callback({ success: false, error: errorResponse });
    }
  });
}
