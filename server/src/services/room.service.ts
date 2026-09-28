import { v4 as uuidv4 } from 'uuid';
import { Player, ConnectionStatus, ControllerType } from '../types/player.types';
import { RoomState } from '../types/room.types';
import { serverConfig } from '../config/env.config';
import { persistenceService } from './persistence.service';
import { logger } from '../utils/logger';

export class RoomService {
  private rooms: Map<string, RoomState> = new Map(); // roomCode -> RoomState
  private socketToPlayerMap: Map<string, { roomCode: string; seatIndex: number }> = new Map();
  private processedActionIds: Set<string> = new Set(); // Duplicate action protection

  /**
   * Generates a unique 4-digit room code (1000..9999)
   */
  private generateUniqueRoomCode(): string {
    let attempts = 0;
    while (attempts < 1000) {
      const code = Math.floor(1000 + Math.random() * 9000).toString();
      if (!this.rooms.has(code)) {
        return code;
      }
      attempts++;
    }
    throw new Error('Server room code capacity exhausted');
  }

  /**
   * Check if action ID was already processed (duplicate protection)
   */
  isActionProcessed(actionId: string): boolean {
    if (!actionId) return false;
    return this.processedActionIds.has(actionId);
  }

  /**
   * Mark action ID as processed
   */
  markActionProcessed(actionId: string): void {
    if (!actionId) return;
    this.processedActionIds.add(actionId);
    // Keep memory clean: prune if exceeding 5000 entries
    if (this.processedActionIds.size > 5000) {
      const entries = Array.from(this.processedActionIds);
      entries.slice(0, 1000).forEach((id) => this.processedActionIds.delete(id));
    }
  }

  /**
   * Create a new room with Player 1 (Host) at Seat 0
   */
  createRoom(
    playerName: string,
    uid: string,
    socketId: string,
    targetScore: number = 300
  ): { room: RoomState; player: Player; sessionToken: string } {
    const roomCode = this.generateUniqueRoomCode();
    const sessionToken = uuidv4();

    const hostPlayer: Player = {
      id: 1,
      uid,
      seatIndex: 0,
      name: playerName.trim() || 'Player 1 (Host)',
      controllerType: 'HUMAN',
      connectionStatus: 'CONNECTED',
      isHost: true,
      sessionToken,
      socketId,
      failedTurnAttempts: 0,
      totalScore: 0
    };

    const room: RoomState = {
      roomCode,
      hostPlayerId: 1,
      phase: 'LOBBY',
      players: [hostPlayer, null, null, null], // Exactly 4 seats
      targetScore,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    this.rooms.set(roomCode, room);
    this.socketToPlayerMap.set(socketId, { roomCode, seatIndex: 0 });

    logger.info('RoomService', `Room created: ${roomCode} by ${hostPlayer.name} [socket: ${socketId}]`);
    persistenceService.saveRoomState(room);

    return { room, player: hostPlayer, sessionToken };
  }

  /**
   * Join an existing room
   */
  joinRoom(
    roomCode: string,
    playerName: string,
    uid: string,
    socketId: string
  ): { room: RoomState; player: Player; sessionToken: string } {
    const room = this.rooms.get(roomCode);
    if (!room) {
      throw new Error(`Room with code "${roomCode}" not found.`);
    }

    if (room.phase === 'CLOSED') {
      throw new Error(`Room "${roomCode}" is closed.`);
    }

    // Find first available seat (0..3)
    const availableSeat = room.players.findIndex((seat) => seat === null);
    if (availableSeat === -1) {
      throw new Error(`Room "${roomCode}" is full (maximum 4 players).`);
    }

    const nextId = availableSeat + 1;
    const sessionToken = uuidv4();

    const newPlayer: Player = {
      id: nextId,
      uid,
      seatIndex: availableSeat,
      name: playerName.trim() || `Player ${nextId}`,
      controllerType: 'HUMAN',
      connectionStatus: 'CONNECTED',
      isHost: false,
      sessionToken,
      socketId,
      failedTurnAttempts: 0,
      totalScore: 0
    };

    room.players[availableSeat] = newPlayer;
    room.updatedAt = Date.now();

    this.socketToPlayerMap.set(socketId, { roomCode, seatIndex: availableSeat });

    logger.info(
      'RoomService',
      `Player joined: ${newPlayer.name} -> Room ${roomCode} at Seat ${availableSeat} [socket: ${socketId}]`
    );
    persistenceService.saveRoomState(room);

    return { room, player: newPlayer, sessionToken };
  }

  /**
   * Add a Bot to the room (Host only, fills next available seat)
   */
  addBot(roomCode: string, requestingSocketId: string): { room: RoomState; botPlayer: Player } {
    const room = this.rooms.get(roomCode);
    if (!room) throw new Error(`Room ${roomCode} not found`);

    const mapping = this.socketToPlayerMap.get(requestingSocketId);
    if (!mapping || mapping.roomCode !== roomCode) {
      throw new Error('Unauthorized');
    }

    const requestingPlayer = room.players[mapping.seatIndex];
    if (!requestingPlayer || !requestingPlayer.isHost) {
      throw new Error('Only the host can add bots to the room.');
    }

    const availableSeat = room.players.findIndex((seat) => seat === null);
    if (availableSeat === -1) {
      throw new Error('Room is already full.');
    }

    const nextId = availableSeat + 1;
    const botPlayer: Player = {
      id: nextId,
      seatIndex: availableSeat,
      name: `Bot ${nextId}`,
      controllerType: 'BOT',
      connectionStatus: 'CONNECTED',
      isHost: false,
      sessionToken: uuidv4(),
      socketId: null,
      failedTurnAttempts: 0,
      totalScore: 0
    };

    room.players[availableSeat] = botPlayer;
    room.updatedAt = Date.now();

    logger.info('RoomService', `Bot added: ${botPlayer.name} -> Room ${roomCode} at Seat ${availableSeat}`);
    persistenceService.saveRoomState(room);

    return { room, botPlayer };
  }

  /**
   * Reconnect a player using their persistent sessionToken
   */
  reconnectPlayer(
    roomCode: string,
    sessionToken: string,
    newSocketId: string
  ): { room: RoomState; player: Player } {
    const room = this.rooms.get(roomCode);
    if (!room) throw new Error(`Room ${roomCode} not found`);

    const playerIndex = room.players.findIndex(
      (p) => p !== null && p.sessionToken === sessionToken
    );

    if (playerIndex === -1) {
      throw new Error('Session token invalid or expired. Reconnection failed.');
    }

    const player = room.players[playerIndex]!;

    // Cancel disconnect timer
    if (player.disconnectTimer) {
      clearTimeout(player.disconnectTimer);
      player.disconnectTimer = null;
    }

    player.connectionStatus = 'CONNECTED';
    player.socketId = newSocketId;
    player.disconnectRemainingSeconds = undefined;
    room.updatedAt = Date.now();

    this.socketToPlayerMap.set(newSocketId, { roomCode, seatIndex: playerIndex });

    logger.info(
      'RoomService',
      `Player reconnected: ${player.name} in Room ${roomCode} [new socket: ${newSocketId}]`
    );
    persistenceService.saveRoomState(room);

    return { room, player };
  }

  /**
   * Handle socket disconnection
   */
  handleDisconnect(
    socketId: string,
    onTimeout: (room: RoomState, player: Player) => void
  ): { room: RoomState; player: Player } | null {
    const mapping = this.socketToPlayerMap.get(socketId);
    if (!mapping) return null;

    this.socketToPlayerMap.delete(socketId);

    const room = this.rooms.get(mapping.roomCode);
    if (!room) return null;

    const player = room.players[mapping.seatIndex];
    if (!player || player.controllerType === 'BOT') return null;

    player.connectionStatus = 'DISCONNECTED';
    player.socketId = null;
    player.disconnectRemainingSeconds = serverConfig.reconnectWindowSeconds;
    room.updatedAt = Date.now();

    logger.warn(
      'RoomService',
      `Player disconnected: ${player.name} from Room ${room.roomCode}. Starting ${serverConfig.reconnectWindowSeconds}s reconnect window.`
    );

    // Start authoritative 30s reconnect countdown
    player.disconnectTimer = setTimeout(() => {
      logger.warn(
        'RoomService',
        `Reconnect window expired for ${player.name} in Room ${room.roomCode}. Assigning BOT takeover.`
      );
      player.connectionStatus = 'BOT_ACTIVE';
      player.controllerType = 'BOT';
      player.disconnectTimer = null;
      player.disconnectRemainingSeconds = 0;
      room.updatedAt = Date.now();
      persistenceService.saveRoomState(room);
      onTimeout(room, player);
    }, serverConfig.reconnectWindowSeconds * 1000);

    persistenceService.saveRoomState(room);

    return { room, player };
  }

  getRoom(roomCode: string): RoomState | undefined {
    return this.rooms.get(roomCode);
  }

  getPlayerBySocket(socketId: string): { room: RoomState; player: Player } | null {
    const mapping = this.socketToPlayerMap.get(socketId);
    if (!mapping) return null;
    const room = this.rooms.get(mapping.roomCode);
    if (!room) return null;
    const player = room.players[mapping.seatIndex];
    if (!player) return null;
    return { room, player };
  }
}

export const roomService = new RoomService();
