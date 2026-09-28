import express, { Request, Response } from 'express';
import http from 'http';
import cors from 'cors';
import { v4 as uuidv4 } from 'uuid';
import { serverConfig } from './config/env.config';
import { initializeFirebase } from './config/firebase.config';
import { initializeSocketServer } from './sockets/socket.server';
import { roomService } from './services/room.service';
import { toPublicRoom } from './types/room.types';
import { toPublicPlayer } from './types/player.types';
import { logger } from './utils/logger';

// 1. Initialize Firebase services
initializeFirebase();

// 2. Setup Express application
const app = express();
app.use(cors({ origin: serverConfig.corsOrigin }));
app.use(express.json());

// Health check endpoint
app.get('/health', (_req: Request, res: Response) => {
  res.status(200).json({
    status: 'ok',
    timestamp: Date.now(),
    env: serverConfig.nodeEnv,
    authMode: serverConfig.authMode
  });
});

// REST: Create Room
app.post('/api/rooms/create', (req: Request, res: Response) => {
  try {
    const { playerName = 'Player 1', uid = uuidv4(), targetScore = 300 } = req.body;
    const socketId = `http_${uuidv4().substring(0, 8)}`;
    const { room, player, sessionToken } = roomService.createRoom(
      playerName,
      uid,
      socketId,
      targetScore
    );

    const publicPlayer = toPublicPlayer(player);
    const publicRoom = toPublicRoom(room);

    res.status(200).json({
      success: true,
      roomCode: room.roomCode,
      player: publicPlayer,
      sessionToken,
      roomState: publicRoom
    });
  } catch (err: any) {
    logger.error('API', 'Error creating room', err);
    res.status(400).json({ success: false, error: err.message });
  }
});

// REST: Join Room
app.post('/api/rooms/join', (req: Request, res: Response) => {
  try {
    const { roomCode, playerName = 'Player', uid = uuidv4() } = req.body;
    const cleanCode = (roomCode || '').trim();
    if (!cleanCode || cleanCode.length !== 4) {
      return res.status(400).json({ success: false, error: 'Room code must be exactly 4 digits.' });
    }

    const socketId = `http_${uuidv4().substring(0, 8)}`;
    const { room, player, sessionToken } = roomService.joinRoom(
      cleanCode,
      playerName,
      uid,
      socketId
    );

    const publicPlayer = toPublicPlayer(player);
    const publicRoom = toPublicRoom(room);

    io.in(cleanCode).emit('player_joined', { player: publicPlayer, roomState: publicRoom });
    io.in(cleanCode).emit('room_state_updated', { roomState: publicRoom });

    res.status(200).json({
      success: true,
      roomCode: room.roomCode,
      player: publicPlayer,
      sessionToken,
      roomState: publicRoom
    });
  } catch (err: any) {
    logger.error('API', 'Error joining room', err);
    res.status(400).json({ success: false, error: err.message });
  }
});

// REST: Get Room State
app.get('/api/rooms/:code', (req: Request, res: Response) => {
  try {
    const code = String(req.params.code || '').trim();
    const room = roomService.getRoom(code);
    if (!room) {
      return res.status(404).json({ success: false, error: `Room ${code} not found.` });
    }
    res.status(200).json({
      success: true,
      roomState: toPublicRoom(room)
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// REST: Add Bot
app.post('/api/rooms/add-bot', (req: Request, res: Response) => {
  try {
    const { roomCode } = req.body;
    const room = roomService.getRoom(roomCode);
    if (!room) return res.status(404).json({ success: false, error: 'Room not found.' });

    // Host socket mapping or fallback
    const host = room.players.find(p => p !== null && p.isHost);
    const hostSocket = host?.socketId || 'http_host';
    const { room: updatedRoom, botPlayer } = roomService.addBot(roomCode, hostSocket);

    const publicRoom = toPublicRoom(updatedRoom);
    const publicBot = toPublicPlayer(botPlayer);

    io.in(roomCode).emit('player_joined', { player: publicBot, roomState: publicRoom });
    io.in(roomCode).emit('room_state_updated', { roomState: publicRoom });

    res.status(200).json({
      success: true,
      roomState: publicRoom
    });
  } catch (err: any) {
    res.status(400).json({ success: false, error: err.message });
  }
});

// Info endpoint
app.get('/', (_req: Request, res: Response) => {
  res.status(200).json({
    name: 'Card Game Authoritative Multiplayer Server',
    version: '1.0.0',
    websocket: 'Socket.IO',
    reconnectWindow: `${serverConfig.reconnectWindowSeconds}s`,
    endpoints: {
      health: '/health',
      createRoom: 'POST /api/rooms/create',
      joinRoom: 'POST /api/rooms/join',
      getRoom: 'GET /api/rooms/:code'
    }
  });
});

// 3. Create HTTP & Socket.IO Server
const httpServer = http.createServer(app);
const io = initializeSocketServer(httpServer);

// 4. Start Server
httpServer.listen(serverConfig.port, serverConfig.host, () => {
  logger.info(
    'Server',
    `Multiplayer Server running at http://${serverConfig.host}:${serverConfig.port} [env: ${serverConfig.nodeEnv}]`
  );
  logger.info(
    'Server',
    `WebSocket ready for connections (pingInterval: ${serverConfig.pingIntervalMs}ms, reconnectWindow: ${serverConfig.reconnectWindowSeconds}s)`
  );
});

// 5. Graceful shutdown
const handleShutdown = (signal: string) => {
  logger.info('Server', `${signal} received: closing server gracefully...`);
  io.close(() => {
    httpServer.close(() => {
      logger.info('Server', 'HTTP server and WebSockets closed.');
      process.exit(0);
    });
  });
};

process.on('SIGTERM', () => handleShutdown('SIGTERM'));
process.on('SIGINT', () => handleShutdown('SIGINT'));

