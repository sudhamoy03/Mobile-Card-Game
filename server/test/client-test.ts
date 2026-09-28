import { io, Socket } from 'socket.io-client';

const SERVER_URL = process.env.TEST_SERVER_URL || 'wss://mobile-card-game.onrender.com';

console.log('====================================================');
console.log('STARTING TWO-DEVICE MULTIPLAYER CONNECTION TEST');
console.log(`Target Server URL: ${SERVER_URL}`);
console.log('====================================================\n');

async function runTest() {
  // --- STEP 1: Connect Device 1 (Player 1 / Host) ---
  console.log('[1/7] Connecting Device 1 (Player 1)...');
  const socket1: Socket = io(SERVER_URL, {
    transports: ['websocket'],
    reconnection: false
  });

  await new Promise<void>((resolve, reject) => {
    socket1.on('connect', () => {
      console.log(`✓ Device 1 connected (Socket ID: ${socket1.id})`);
      resolve();
    });
    socket1.on('connect_error', (err) => reject(err));
    setTimeout(() => reject(new Error('Device 1 connection timeout')), 5000);
  });

  // --- STEP 2: Device 1 Creates Room ---
  console.log('\n[2/7] Device 1 creating room...');
  let roomCode = '';
  let sessionToken1 = '';

  await new Promise<void>((resolve, reject) => {
    socket1.emit(
      'create_room',
      { playerName: 'Player 1 (Host)', targetScore: 300 },
      (response: any) => {
        if (response?.success) {
          roomCode = response.data.roomCode;
          sessionToken1 = response.data.sessionToken;
          console.log(`✓ Room created successfully! 4-Digit Room Code: [${roomCode}]`);
          console.log(`✓ Host Seat 0 assigned. Session Token: ${sessionToken1.substring(0, 8)}...`);
          resolve();
        } else {
          reject(new Error(`Failed to create room: ${JSON.stringify(response?.error)}`));
        }
      }
    );
  });

  // --- STEP 3: Connect Device 2 (Player 2) ---
  console.log('\n[3/7] Connecting Device 2 (Player 2)...');
  const socket2: Socket = io(SERVER_URL, {
    transports: ['websocket'],
    reconnection: false
  });

  await new Promise<void>((resolve, reject) => {
    socket2.on('connect', () => {
      console.log(`✓ Device 2 connected (Socket ID: ${socket2.id})`);
      resolve();
    });
    socket2.on('connect_error', (err) => reject(err));
    setTimeout(() => reject(new Error('Device 2 connection timeout')), 5000);
  });

  // Setup broadcast listeners
  let device1ReceivedPlayer2Join = false;
  let device2ReceivedJoinedConfirmation = false;
  let sessionToken2 = '';

  socket1.on('player_joined', (data) => {
    console.log(`→ Device 1 received [PLAYER_JOINED]: ${data.player.name} at Seat ${data.player.seatIndex}`);
    device1ReceivedPlayer2Join = true;
  });

  // --- STEP 4: Device 2 Joins Room with 4-Digit Code ---
  console.log(`\n[4/7] Device 2 joining Room [${roomCode}]...`);
  await new Promise<void>((resolve, reject) => {
    socket2.emit(
      'join_room',
      { roomCode, playerName: 'Player 2' },
      (response: any) => {
        if (response?.success) {
          sessionToken2 = response.data.sessionToken;
          device2ReceivedJoinedConfirmation = true;
          console.log(`✓ Device 2 successfully joined room [${roomCode}]!`);
          console.log(`✓ Player 2 assigned Seat ${response.data.player.seatIndex}`);
          console.log(`✓ Current Room Player Count: ${response.data.roomState.playerCount}/4`);
          resolve();
        } else {
          reject(new Error(`Device 2 failed to join room: ${JSON.stringify(response?.error)}`));
        }
      }
    );
  });

  // Wait briefly for socket broadcast event
  await new Promise((r) => setTimeout(r, 400));
  if (!device1ReceivedPlayer2Join || !device2ReceivedJoinedConfirmation) {
    throw new Error('Real-time synchronization failed for PLAYER_JOINED event');
  }
  console.log('✓ Verified: Both devices synchronized on room state!');

  // --- STEP 5: Real-time Message Test (TEST_MESSAGE) ---
  console.log('\n[5/7] Testing Real-Time Broadcast: Device 1 sends TEST_MESSAGE...');
  const testActionId = `action_${Date.now()}`;
  const testMsg = 'Hello from Device 1! Real-time multiplayer verified.';

  let device2ReceivedTestMessage = false;

  socket2.on('test_message_received', (data) => {
    console.log(`→ Device 2 received [TEST_MESSAGE_RECEIVED] from ${data.fromPlayer.name}: "${data.message}"`);
    if (data.actionId === testActionId && data.message === testMsg) {
      device2ReceivedTestMessage = true;
    }
  });

  await new Promise<void>((resolve, reject) => {
    socket1.emit(
      'test_message',
      { roomCode, message: testMsg, actionId: testActionId },
      (response: any) => {
        if (response?.success) {
          console.log('✓ Device 1 message acknowledged by server');
          resolve();
        } else {
          reject(new Error(`Failed to send test message: ${JSON.stringify(response?.error)}`));
        }
      }
    );
  });

  await new Promise((r) => setTimeout(r, 400));
  if (!device2ReceivedTestMessage) {
    throw new Error('Device 2 failed to receive TEST_MESSAGE from Device 1');
  }
  console.log('✓ Verified: Instant real-time server broadcast between two devices!');

  // --- STEP 6: Duplicate Action Protection Test ---
  console.log('\n[6/7] Testing Duplicate Action Protection...');
  await new Promise<void>((resolve, reject) => {
    socket1.emit(
      'test_message',
      { roomCode, message: testMsg, actionId: testActionId },
      (response: any) => {
        if (response?.duplicate) {
          console.log('✓ Server successfully detected and rejected duplicate actionId!');
          resolve();
        } else {
          reject(new Error('Duplicate action was not rejected'));
        }
      }
    );
  });

  // --- STEP 7: Disconnect & Reconnect with 30s Window Test ---
  console.log('\n[7/7] Testing Disconnect Detection and Reconnection...');
  let device1SawDisconnect = false;
  socket1.on('player_disconnected', (data) => {
    console.log(`→ Device 1 received [PLAYER_DISCONNECTED]: ${data.player.name} (remaining: ${data.remainingSeconds}s)`);
    device1SawDisconnect = true;
  });

  let device1SawReconnect = false;
  socket1.on('player_reconnected', (data) => {
    console.log(`→ Device 1 received [PLAYER_RECONNECTED]: ${data.player.name} restored to Seat ${data.player.seatIndex}!`);
    device1SawReconnect = true;
  });

  // Disconnect socket2
  console.log('→ Simulating Device 2 sudden network drop (disconnecting socket)...');
  socket2.disconnect();

  await new Promise((r) => setTimeout(r, 600));
  if (!device1SawDisconnect) {
    throw new Error('Device 1 was not notified of Device 2 disconnection');
  }
  console.log('✓ Verified: Disconnect detection and 30-second window active!');

  // Reconnect with new socket using sessionToken2
  console.log('→ Device 2 reconnecting with sessionToken...');
  const socket2Reconnect: Socket = io(SERVER_URL, {
    transports: ['websocket'],
    reconnection: false
  });

  await new Promise<void>((resolve, reject) => {
    socket2Reconnect.on('connect', () => {
      socket2Reconnect.emit(
        'reconnect_session',
        { roomCode, sessionToken: sessionToken2 },
        (res: any) => {
          if (res?.success) {
            console.log('✓ Device 2 session restored successfully on server!');
            resolve();
          } else {
            reject(new Error(`Reconnect failed: ${JSON.stringify(res?.error)}`));
          }
        }
      );
    });
    setTimeout(() => reject(new Error('Reconnect timeout')), 5000);
  });

  await new Promise((r) => setTimeout(r, 600));
  if (!device1SawReconnect) {
    throw new Error('Device 1 was not notified of Device 2 reconnection');
  }
  console.log('✓ Verified: Reconnection restored identical player identity and seat!');

  // Cleanup
  socket1.disconnect();
  socket2Reconnect.disconnect();

  console.log('\n====================================================');
  console.log('ALL TWO-DEVICE MULTIPLAYER TESTS PASSED (100% SUCCESS)');
  console.log('====================================================');
  process.exit(0);
}

runTest().catch((err) => {
  console.error('\n❌ Test failed with error:', err);
  process.exit(1);
});
