import { Server as HttpServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import { roomService } from '../services/room.service';
import { toPublicRoom } from '../types/room.types';
import { toPublicPlayer } from '../types/player.types';
import { logger } from '../utils/logger';

interface ExtendedWebSocket extends WebSocket {
  id: string;
  roomCode?: string;
  isAlive: boolean;
}

export class NativeWebSocketServer {
  private wss: WebSocketServer | null = null;
  private roomToSocketsMap: Map<string, Set<ExtendedWebSocket>> = new Map();

  initialize(httpServer: HttpServer): WebSocketServer {
    this.wss = new WebSocketServer({ server: httpServer, path: '/ws' });

    this.wss.on('connection', (wsRaw: WebSocket) => {
      const ws = wsRaw as ExtendedWebSocket;
      ws.id = uuidv4();
      ws.isAlive = true;

      logger.info('WS', `Client connected via native WebSocket: ${ws.id}`);

      ws.on('pong', () => {
        ws.isAlive = true;
      });

      ws.on('message', (message: string) => {
        try {
          const raw = message.toString();
          const parsed = JSON.parse(raw);
          this.handleMessage(ws, parsed);
        } catch (err: any) {
          logger.error('WS', `Error handling message from ${ws.id}`, err);
          this.send(ws, {
            event: 'error',
            data: { code: 'INVALID_MESSAGE', message: err.message }
          });
        }
      });

      ws.on('close', () => {
        logger.info('WS', `Client disconnected: ${ws.id}`);
        this.handleDisconnect(ws);
      });

      ws.on('error', (err) => {
        logger.error('WS', `Socket error for ${ws.id}`, err);
      });
    });

    // Heartbeat ping interval
    const interval = setInterval(() => {
      this.wss?.clients.forEach((client) => {
        const ws = client as ExtendedWebSocket;
        if (!ws.isAlive) {
          logger.warn('WS', `Terminating inactive socket: ${ws.id}`);
          return ws.terminate();
        }
        ws.isAlive = false;
        ws.ping();
      });
    }, 15000);

    this.wss.on('close', () => {
      clearInterval(interval);
    });

    return this.wss;
  }

  private handleMessage(ws: ExtendedWebSocket, message: { event: string; data?: any }) {
    const { event, data = {} } = message;

    switch (event) {
      case 'ping': {
        this.send(ws, {
          event: 'pong',
          data: {
            timestamp: Date.now(),
            clientTime: data.clientTime || data.timestamp
          }
        });
        break;
      }

      case 'create_room': {
        try {
          const playerName = data.playerName || 'Player 1';
          const uid = data.uid || uuidv4();
          const targetScore = data.targetScore || 300;

          const { room, player, sessionToken } = roomService.createRoom(
            playerName,
            uid,
            ws.id,
            targetScore
          );

          ws.roomCode = room.roomCode;
          this.addSocketToRoom(room.roomCode, ws);

          const response = {
            roomCode: room.roomCode,
            player: toPublicPlayer(player),
            sessionToken,
            roomState: toPublicRoom(room)
          };

          this.send(ws, { event: 'room_created', data: response });
        } catch (error: any) {
          this.send(ws, {
            event: 'error',
            data: { code: 'CREATE_ROOM_FAILED', message: error.message }
          });
        }
        break;
      }

      case 'join_room': {
        try {
          const roomCode = data.roomCode?.trim();
          if (!roomCode || roomCode.length !== 4) {
            throw new Error('Room code must be exactly 4 digits.');
          }

          const playerName = data.playerName || 'Player';
          const uid = data.uid || uuidv4();

          const { room, player, sessionToken } = roomService.joinRoom(
            roomCode,
            playerName,
            uid,
            ws.id
          );

          ws.roomCode = roomCode;
          this.addSocketToRoom(roomCode, ws);

          const joinedResponse = {
            roomCode: room.roomCode,
            player: toPublicPlayer(player),
            sessionToken,
            roomState: toPublicRoom(room)
          };

          // Send confirmation to joining player
          this.send(ws, { event: 'room_joined', data: joinedResponse });

          // Broadcast to all other players in room
          this.broadcastToRoom(roomCode, {
            event: 'player_joined',
            data: {
              player: toPublicPlayer(player),
              roomState: toPublicRoom(room)
            }
          }, ws);

          // Broadcast updated room state to all players in room
          this.broadcastToRoom(roomCode, {
            event: 'room_state_updated',
            data: { roomState: toPublicRoom(room) }
          });
        } catch (error: any) {
          this.send(ws, {
            event: 'error',
            data: { code: 'JOIN_ROOM_FAILED', message: error.message }
          });
        }
        break;
      }

      case 'add_bot': {
        try {
          const roomCode = data.roomCode || ws.roomCode;
          if (!roomCode) throw new Error('No room specified.');

          const { room, botPlayer } = roomService.addBot(roomCode, ws.id);

          this.broadcastToRoom(roomCode, {
            event: 'player_joined',
            data: {
              player: toPublicPlayer(botPlayer),
              roomState: toPublicRoom(room)
            }
          });

          this.broadcastToRoom(roomCode, {
            event: 'room_state_updated',
            data: { roomState: toPublicRoom(room) }
          });
        } catch (error: any) {
          this.send(ws, {
            event: 'error',
            data: { code: 'ADD_BOT_FAILED', message: error.message }
          });
        }
        break;
      }

      case 'reconnect_session': {
        try {
          const roomCode = data.roomCode?.trim();
          const sessionToken = data.sessionToken;
          if (!roomCode || !sessionToken) throw new Error('Missing roomCode or sessionToken.');

          const { room, player } = roomService.reconnectPlayer(roomCode, sessionToken, ws.id);
          ws.roomCode = roomCode;
          this.addSocketToRoom(roomCode, ws);

          this.send(ws, {
            event: 'session_restored',
            data: {
              roomCode: room.roomCode,
              player: toPublicPlayer(player),
              roomState: toPublicRoom(room)
            }
          });

          this.broadcastToRoom(roomCode, {
            event: 'player_reconnected',
            data: {
              player: toPublicPlayer(player),
              roomState: toPublicRoom(room)
            }
          });

          this.broadcastToRoom(roomCode, {
            event: 'room_state_updated',
            data: { roomState: toPublicRoom(room) }
          });
        } catch (error: any) {
          this.send(ws, {
            event: 'error',
            data: { code: 'RECONNECT_FAILED', message: error.message }
          });
        }
        break;
      }

      default:
        logger.warn('WS', `Unknown event: ${event}`);
    }
  }

  private handleDisconnect(ws: ExtendedWebSocket) {
    if (!ws.roomCode) return;
    const roomCode = ws.roomCode;
    this.removeSocketFromRoom(roomCode, ws);

    const result = roomService.handleDisconnect(ws.id, (room, player) => {
      this.broadcastToRoom(room.roomCode, {
        event: 'player_timeout_bot_assigned',
        data: {
          player: toPublicPlayer(player),
          roomState: toPublicRoom(room)
        }
      });
      this.broadcastToRoom(room.roomCode, {
        event: 'room_state_updated',
        data: { roomState: toPublicRoom(room) }
      });
    });

    if (result) {
      this.broadcastToRoom(roomCode, {
        event: 'player_disconnected',
        data: {
          player: toPublicPlayer(result.player),
          roomState: toPublicRoom(result.room)
        }
      });
      this.broadcastToRoom(roomCode, {
        event: 'room_state_updated',
        data: { roomState: toPublicRoom(result.room) }
      });
    }
  }

  broadcastToRoom(roomCode: string, payload: { event: string; data: any }, exclude?: ExtendedWebSocket) {
    const sockets = this.roomToSocketsMap.get(roomCode);
    if (!sockets) return;

    const json = JSON.stringify(payload);
    sockets.forEach((s) => {
      if (s !== exclude && s.readyState === WebSocket.OPEN) {
        s.send(json);
      }
    });
  }

  private addSocketToRoom(roomCode: string, ws: ExtendedWebSocket) {
    let set = this.roomToSocketsMap.get(roomCode);
    if (!set) {
      set = new Set();
      this.roomToSocketsMap.set(roomCode, set);
    }
    set.add(ws);
  }

  private removeSocketFromRoom(roomCode: string, ws: ExtendedWebSocket) {
    const set = this.roomToSocketsMap.get(roomCode);
    if (set) {
      set.delete(ws);
      if (set.size === 0) {
        this.roomToSocketsMap.delete(roomCode);
      }
    }
  }

  private send(ws: WebSocket, payload: { event: string; data: any }) {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(payload));
    }
  }
}

export const nativeWebSocketServer = new NativeWebSocketServer();
