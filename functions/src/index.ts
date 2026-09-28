import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

admin.initializeApp();
const db = admin.firestore();

/**
 * Cloud Function: reservePlayerId
 * Trusted server-side reservation of a globally unique Player ID.
 */
export const reservePlayerId = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'The function must be called while authenticated.'
    );
  }

  const uid = context.auth.uid;
  const rawId: string = data.playerId;
  const displayName: string = data.displayName || 'Player';
  const avatarType: string = data.avatarType || 'preset';
  const avatarId: string = data.avatarId || 'preset_01';

  if (!rawId || typeof rawId !== 'string') {
    throw new functions.https.HttpsError('invalid-argument', 'Player ID is required.');
  }

  const cleanId = rawId.trim().removePrefix('@').toUpperCase();
  const normalizedId = cleanId.toLowerCase();

  if (cleanId.length < 3 || cleanId.length > 15 || !/^[A-Z0-9_]+$/.test(cleanId)) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Player ID must be 3-15 alphanumeric characters.'
    );
  }

  const fullId = `@${cleanId}`;

  return await db.runTransaction(async (transaction) => {
    const reservationRef = db.collection('playerIds').document(normalizedId);
    const existing = await transaction.get(reservationRef);

    if (existing.exists && existing.data()?.uid !== uid) {
      throw new functions.https.HttpsError(
        'already-exists',
        `Player ID ${fullId} is already taken.`
      );
    }

    const now = Date.now();
    // 1. Reserve ID
    transaction.set(reservationRef, {
      uid,
      createdAt: now
    });

    // 2. Set Profile
    const userRef = db.collection('users').document(uid);
    transaction.set(
      userRef,
      {
        uid,
        displayName: displayName.trim().substring(0, 30),
        playerId: fullId,
        normalizedPlayerId: normalizedId,
        avatarType,
        avatarId,
        accountType: context.auth?.token.firebase?.sign_in_provider === 'anonymous' ? 'GUEST' : 'GOOGLE',
        updatedAt: now,
        createdAt: now
      },
      { merge: true }
    );

    return { success: true, playerId: fullId };
  });
});

/**
 * Cloud Function: changePlayerId
 * Atomically swaps user's Player ID and releases the old one.
 */
export const changePlayerId = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Must be authenticated.');
  }

  const uid = context.auth.uid;
  const newRawId: string = data.newPlayerId;
  if (!newRawId) {
    throw new functions.https.HttpsError('invalid-argument', 'New Player ID is required.');
  }

  const cleanNew = newRawId.trim().removePrefix('@').toUpperCase();
  const newNormalized = cleanNew.toLowerCase();
  const fullNewId = `@${cleanNew}`;

  return await db.runTransaction(async (transaction) => {
    const userRef = db.collection('users').document(uid);
    const userSnap = await transaction.get(userRef);

    if (!userSnap.exists) {
      throw new functions.https.HttpsError('not-found', 'User profile not found.');
    }

    const oldNormalized: string = userSnap.data()?.normalizedPlayerId || '';
    if (oldNormalized === newNormalized) {
      return { success: true, playerId: fullNewId };
    }

    // Check new ID availability
    const newReservationRef = db.collection('playerIds').document(newNormalized);
    const existing = await transaction.get(newReservationRef);

    if (existing.exists && existing.data()?.uid !== uid) {
      throw new functions.https.HttpsError(
        'already-exists',
        `Player ID ${fullNewId} is already taken.`
      );
    }

    const now = Date.now();
    // Claim new ID
    transaction.set(newReservationRef, { uid, createdAt: now });

    // Release old ID
    if (oldNormalized) {
      const oldReservationRef = db.collection('playerIds').document(oldNormalized);
      transaction.delete(oldReservationRef);
    }

    // Update profile
    transaction.update(userRef, {
      playerId: fullNewId,
      normalizedPlayerId: newNormalized,
      updatedAt: now
    });

    return { success: true, playerId: fullNewId };
  });
});
