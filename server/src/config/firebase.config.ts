import * as admin from 'firebase-admin';
import fs from 'fs';
import { logger } from '../utils/logger';

let isFirebaseInitialized = false;

export function initializeFirebase(): void {
  try {
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_KEY_PATH;

    if (serviceAccountPath && fs.existsSync(serviceAccountPath)) {
      const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        databaseURL: process.env.FIREBASE_DATABASE_URL
      });
      isFirebaseInitialized = true;
      logger.info('Firebase', 'Firebase Admin initialized via service account file');
      return;
    }

    if (process.env.FIREBASE_PROJECT_ID && process.env.FIREBASE_CLIENT_EMAIL && process.env.FIREBASE_PRIVATE_KEY) {
      admin.initializeApp({
        credential: admin.credential.cert({
          projectId: process.env.FIREBASE_PROJECT_ID,
          clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
          privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n')
        }),
        databaseURL: process.env.FIREBASE_DATABASE_URL
      });
      isFirebaseInitialized = true;
      logger.info('Firebase', 'Firebase Admin initialized via environment variables');
      return;
    }

    logger.warn(
      'Firebase',
      'No Firebase credentials provided. Running in dev authentication mode (dev tokens accepted)'
    );
  } catch (error) {
    logger.error('Firebase', 'Failed to initialize Firebase Admin SDK', error);
  }
}

export function getFirebaseAdmin(): typeof admin | null {
  return isFirebaseInitialized ? admin : null;
}

export function isFirebaseReady(): boolean {
  return isFirebaseInitialized;
}
