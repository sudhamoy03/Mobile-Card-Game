import { Server as HttpServer } from 'http';
import { Server as SocketIOServer, Socket } from 'socket.io';
import { serverConfig } from '../config/env.config';
import { registerRoomHandlers } from './handlers/room.handler';
import { registerTestHandlers } from './handlers/test.handler';
import { registerConnectionHandlers } from './handlers/connection.handler';
import { logger } from '../utils/logger';

export function initializeSocketServer(httpServer: HttpServer): SocketIOServer {
  const io = new SocketIOServer(httpServer, {
    cors: {
      origin: serverConfig.corsOrigin,
      methods: ['GET', 'POST'],
      credentials: true
    },
    transports: ['websocket', 'polling'], // Real persistent WebSocket prioritized
    pingInterval: serverConfig.pingIntervalMs,
    pingTimeout: serverConfig.pingTimeoutMs,
    maxHttpBufferSize: 1e6 // 1 MB limit
  });

  io.on('connection', (socket: Socket) => {
    logger.info('SocketServer', `New client connected: ${socket.id} (transport: ${socket.conn.transport.name})`);

    // Register modular event handlers
    registerRoomHandlers(io, socket);
    registerTestHandlers(io, socket);
    registerConnectionHandlers(io, socket);

    socket.on('error', (err) => {
      logger.error('SocketServer', `Socket error on ${socket.id}`, err);
    });
  });

  return io;
}
