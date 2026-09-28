import { Socket, Server } from 'socket.io';
import { roomService } from '../../services/room.service';
import { toPublicPlayer } from '../../types/player.types';
import { toPublicRoom } from '../../types/room.types';
import { PlayerDisconnectedEvent } from '../../types/events.types';
import { logger } from '../../utils/logger';

export function registerConnectionHandlers(io: Server, socket: Socket): void {
  /**
   * Handle socket disconnect
   * Marks player as DISCONNECTED and notifies remaining players in the room with 30s countdown
   */
  socket.on('disconnect', (reason: string) => {
    logger.info('Socket:Connection', `Socket disconnected: ${socket.id} (reason: ${reason})`);

    const result = roomService.handleDisconnect(socket.id, (room, timedOutPlayer) => {
      // Callback invoked after 30s reconnect window expires (authoritative BOT takeover)
      logger.warn(
        'Socket:Connection',
        `Notifying room ${room.roomCode} of permanent bot takeover for ${timedOutPlayer.name}`
      );
      io.in(room.roomCode).emit('room_state_updated', { roomState: toPublicRoom(room) });
    });

    if (result) {
      const { room, player } = result;

      const disconnectEvent: PlayerDisconnectedEvent = {
        player: toPublicPlayer(player),
        remainingSeconds: player.disconnectRemainingSeconds || 30,
        roomState: toPublicRoom(room)
      };

      // Broadcast PLAYER_DISCONNECTED to room
      io.in(room.roomCode).emit('player_disconnected', disconnectEvent);
      io.in(room.roomCode).emit('room_state_updated', { roomState: toPublicRoom(room) });
    }
  });

  /**
   * Heartbeat ping-pong
   */
  socket.on('ping', () => {
    socket.emit('pong', { timestamp: Date.now() });
  });
}
