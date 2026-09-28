export type ConnectionStatus =
  | 'CONNECTED'
  | 'DISCONNECTED'
  | 'RECONNECTING'
  | 'BOT_ACTIVE';

export type ControllerType = 'HUMAN' | 'BOT';

export interface Player {
  id: number;
  uid?: string; // Firebase Auth UID if authenticated
  seatIndex: number; // 0, 1, 2, 3
  name: string;
  controllerType: ControllerType;
  connectionStatus: ConnectionStatus;
  isHost: boolean;
  sessionToken: string; // Persistent token for reconnect verification
  socketId: string | null;
  disconnectTimer?: NodeJS.Timeout | null;
  disconnectRemainingSeconds?: number;
  failedTurnAttempts: number;
  totalScore: number;
}

export interface PlayerPublicView {
  id: number;
  seatIndex: number;
  name: string;
  controllerType: ControllerType;
  connectionStatus: ConnectionStatus;
  isHost: boolean;
  totalScore: number;
  disconnectRemainingSeconds?: number;
}

export function toPublicPlayer(player: Player): PlayerPublicView {
  return {
    id: player.id,
    seatIndex: player.seatIndex,
    name: player.name,
    controllerType: player.controllerType,
    connectionStatus: player.connectionStatus,
    isHost: player.isHost,
    totalScore: player.totalScore,
    disconnectRemainingSeconds: player.disconnectRemainingSeconds
  };
}
