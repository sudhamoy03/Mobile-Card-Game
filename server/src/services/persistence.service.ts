import { getFirebaseAdmin, isFirebaseReady } from '../config/firebase.config';
import { RoomState, toPublicRoom } from '../types/room.types';
import { logger } from '../utils/logger';

export class PersistenceService {
  /**
   * Persist room state to Firebase Realtime Database if connected
   */
  async saveRoomState(room: RoomState): Promise<void> {
    if (!isFirebaseReady()) return;

    try {
      const admin = getFirebaseAdmin();
      if (!admin) return;

      const db = admin.database();
      const publicView = toPublicRoom(room);
      await db.ref(`rooms/${room.roomCode}`).set({
        ...publicView,
        lastPersistedAt: Date.now()
      });
      logger.debug('PersistenceService', `Room ${room.roomCode} synced to Firebase RTDB`);
    } catch (error) {
      logger.error('PersistenceService', `Failed to sync room ${room.roomCode} to Firebase RTDB`, error);
    }
  }

  /**
   * Remove room from persistence when game is closed
   */
  async deleteRoom(roomCode: string): Promise<void> {
    if (!isFirebaseReady()) return;

    try {
      const admin = getFirebaseAdmin();
      if (!admin) return;

      await admin.database().ref(`rooms/${roomCode}`).remove();
      logger.debug('PersistenceService', `Room ${roomCode} removed from Firebase RTDB`);
    } catch (error) {
      logger.error('PersistenceService', `Failed to delete room ${roomCode} from Firebase RTDB`, error);
    }
  }
}

export const persistenceService = new PersistenceService();
