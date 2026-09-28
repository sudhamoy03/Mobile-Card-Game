import { Player, PlayerPublicView, toPublicPlayer } from './player.types';

export type RoomPhase = 'LOBBY' | 'PLAYING' | 'ROUND_END' | 'CLOSED';

export interface RoomState {
  roomCode: string; // 4-digit code (e.g. "4821")
  hostPlayerId: number;
  phase: RoomPhase;
  players: (Player | null)[]; // Exactly 4 seats (index 0..3)
  targetScore: number;
  createdAt: number;
  updatedAt: number;
  lastActionId?: string;
}

export interface RoomPublicView {
  roomCode: string;
  hostPlayerId: number;
  phase: RoomPhase;
  players: (PlayerPublicView | null)[];
  targetScore: number;
  playerCount: number;
  isFull: boolean;
  createdAt: number;
  updatedAt: number;
}

export function toPublicRoom(room: RoomState): RoomPublicView {
  const publicPlayers = room.players.map((p) => (p ? toPublicPlayer(p) : null));
  const activeCount = room.players.filter((p) => p !== null).length;

  return {
    roomCode: room.roomCode,
    hostPlayerId: room.hostPlayerId,
    phase: room.phase,
    players: publicPlayers,
    targetScore: room.targetScore,
    playerCount: activeCount,
    isFull: activeCount >= 4,
    createdAt: room.createdAt,
    updatedAt: room.updatedAt
  };
}
