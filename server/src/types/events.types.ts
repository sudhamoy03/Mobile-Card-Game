import { PlayerPublicView } from './player.types';
import { RoomPublicView } from './room.types';

// =========================================================================
// Client to Server Events
// =========================================================================

export interface CreateRoomPayload {
  playerName: string;
  targetScore?: number;
  idToken?: string; // Optional Firebase Auth ID token
}

export interface JoinRoomPayload {
  roomCode: string;
  playerName: string;
  idToken?: string;
  sessionToken?: string; // If reconnecting
}

export interface ReconnectPayload {
  roomCode: string;
  sessionToken: string;
  idToken?: string;
}

export interface AddBotPayload {
  roomCode: string;
  actionId: string;
}

export interface TestMessagePayload {
  roomCode: string;
  message: string;
  actionId: string;
}

// =========================================================================
// Server to Client Events
// =========================================================================

export interface RoomCreatedResponse {
  roomCode: string;
  player: PlayerPublicView;
  sessionToken: string;
  roomState: RoomPublicView;
}

export interface RoomJoinedResponse {
  roomCode: string;
  player: PlayerPublicView;
  sessionToken: string;
  roomState: RoomPublicView;
}

export interface PlayerJoinedEvent {
  player: PlayerPublicView;
  roomState: RoomPublicView;
}

export interface PlayerConnectedEvent {
  player: PlayerPublicView;
  roomState: RoomPublicView;
}

export interface PlayerDisconnectedEvent {
  player: PlayerPublicView;
  remainingSeconds: number;
  roomState: RoomPublicView;
}

export interface PlayerReconnectedEvent {
  player: PlayerPublicView;
  roomState: RoomPublicView;
}

export interface RoomStateUpdatedEvent {
  roomState: RoomPublicView;
}

export interface TestMessageBroadcastEvent {
  fromPlayer: PlayerPublicView;
  message: string;
  actionId: string;
  timestamp: number;
}

export interface ErrorResponse {
  code: string;
  message: string;
  actionId?: string;
}
