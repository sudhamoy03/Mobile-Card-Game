import { getFirebaseAdmin, isFirebaseReady } from '../config/firebase.config';
import { serverConfig } from '../config/env.config';
import { logger } from '../utils/logger';

export interface AuthenticatedUser {
  uid: string;
  name: string;
  isAnonymous: boolean;
}

export class AuthService {
  /**
   * Verify token from client (Firebase ID token or development token)
   */
  async verifyToken(idToken?: string, fallbackName?: string): Promise<AuthenticatedUser> {
    if (serverConfig.authMode === 'firebase' && isFirebaseReady()) {
      if (!idToken) {
        throw new Error('Authentication required: Missing Firebase ID token');
      }

      try {
        const admin = getFirebaseAdmin()!;
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        return {
          uid: decodedToken.uid,
          name: decodedToken.name || fallbackName || `Player_${decodedToken.uid.substring(0, 5)}`,
          isAnonymous: decodedToken.firebase?.sign_in_provider === 'anonymous'
        };
      } catch (error: any) {
        logger.error('AuthService', 'Token verification failed', error.message);
        throw new Error(`Authentication failed: ${error.message}`);
      }
    }

    // Development Mode: Generate / accept dev token
    const devUid = idToken ? `dev_${idToken.substring(0, 12)}` : `dev_${Math.random().toString(36).substring(2, 9)}`;
    return {
      uid: devUid,
      name: fallbackName || `Player_${devUid.substring(4, 9)}`,
      isAnonymous: true
    };
  }
}

export const authService = new AuthService();
