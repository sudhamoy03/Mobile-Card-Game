# Authoritative Multiplayer Game Server

Production-ready, authoritative Node.js & TypeScript multiplayer backend for the 4-player mobile card game using **Socket.IO / WebSockets**, **Firebase Authentication**, and **Firebase Realtime Database**.

---

## 1. Architecture & Why Two Devices Previously Failed to Connect

In the legacy client code, multiplayer was simulated purely in local Android ViewModel state (`_gameState.value`):
- Creating a room simply generated an in-memory random number (`Random.nextInt(1000, 9999)`).
- Joining a room created a dummy local `Host Player` and `Player 2` on the joining phone itself.
- There was **no WebSocket server**, **no centralized room registry**, and **no network communication** between physical phones. Device 1 and Device 2 each lived in their own isolated memory spaces.

This authoritative server resolves this by acting as the **single source of truth** for all rooms, seats, connections, heartbeats, and actions.

---

## 2. Complete Backend Folder Structure

```text
/server
├── .env.example                     # Template for environment variables
├── package.json                     # Node.js project manifest & dependencies
├── tsconfig.json                    # TypeScript compiler configuration
├── README.md                        # Documentation & setup instructions
├── src
│   ├── config
│   │   ├── env.config.ts            # Validated environment configuration
│   │   └── firebase.config.ts       # Firebase Admin SDK initialization
│   ├── types
│   │   ├── events.types.ts          # Socket.IO client/server event payloads
│   │   ├── player.types.ts          # Player models, connection states & seat types
│   │   └── room.types.ts            # Room state, phases & public view mappers
│   ├── services
│   │   ├── auth.service.ts          # Firebase token verification & dev auth
│   │   ├── persistence.service.ts   # Firebase RTDB persistence
│   │   └── room.service.ts          # 4-digit code generator, seats & 30s reconnect window
│   ├── sockets
│   │   ├── handlers
│   │   │   ├── connection.handler.ts# Disconnect lifecycle & heartbeat
│   │   │   ├── room.handler.ts      # create_room, join_room, reconnect
│   │   │   └── test.handler.ts      # Real-time test message broadcast & deduplication
│   │   └── socket.server.ts         # Socket.IO server setup (WebSocket transport prioritization)
│   ├── utils
│   │   └── logger.ts                # Structured server logging
│   └── index.ts                     # Express + HTTP + Socket.IO server entry point
└── test
    └── client-test.ts               # End-to-end two-device connection test script
```

---

## 3. Required npm Packages

### Production Dependencies
- `express`: HTTP server for health checks and service discovery.
- `socket.io`: Persistent real-time WebSocket protocol engine with fallback transports.
- `cors`: Cross-Origin Resource Sharing middleware.
- `dotenv`: Environment variable loader.
- `uuid`: Secure unique session tokens and action ID generation.
- `firebase-admin`: Server-side token validation and Realtime Database synchronization.

### Development Dependencies
- `typescript`: Type-safe compilation.
- `ts-node`: Execution of TypeScript without pre-compiling.
- `@types/node`, `@types/express`, `@types/cors`, `@types/uuid`: Type definitions.
- `socket.io-client`: Testing automated multi-device flows.

---

## 4. Required Environment Variables

Defined in `/server/.env` (from `/server/.env.example`):

| Variable | Default | Description |
|---|---|---|
| `PORT` | `4000` | Port for HTTP and WebSocket server |
| `HOST` | `0.0.0.0` | Bind address (0.0.0.0 allows LAN & cloud connections) |
| `NODE_ENV` | `development` | Server runtime environment |
| `CORS_ORIGIN` | `*` | Allowed client origins |
| `PING_INTERVAL_MS`| `10000` | Heartbeat ping frequency (10 seconds) |
| `PING_TIMEOUT_MS` | `5000` | Socket timeout duration |
| `RECONNECT_WINDOW_SECONDS`| `30` | Grace period to restore seat before bot takeover |
| `AUTH_MODE` | `dev` | Set to `firebase` for production, `dev` for local testing |
| `FIREBASE_DATABASE_URL` | Optional | URL to Firebase Realtime Database |
| `FIREBASE_SERVICE_ACCOUNT_KEY_PATH` | Optional | Path to Firebase service account JSON |

---

## 5. How to Install and Run the Backend Locally

1. Open a terminal in the `/server` directory:
   ```bash
   cd server
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Run in development mode (auto-reloading with TypeScript):
   ```bash
   npm run dev
   ```

4. Or compile and run in production mode:
   ```bash
   npm run build
   npm start
   ```

The server will log:
```text
[INFO] [Server] Multiplayer Server running at http://0.0.0.0:4000 [env: development]
[INFO] [Server] WebSocket ready for connections (pingInterval: 10000ms, reconnectWindow: 30s)
```

---

## 6. How to Test Two Devices Connecting to the Same Room

We provide an automated, end-to-end verification script (`/server/test/client-test.ts`) that simulates Device 1 and Device 2 over real WebSockets:

Run the test:
```bash
npm run test:client
```

### What This Test Verifies:
1. **Device 1 (Host)** connects via WebSocket and calls `create_room`.
2. Server allocates **Seat 0**, generates a persistent `sessionToken`, and issues a unique **4-digit room code** (e.g. `4821`).
3. **Device 2 (Player 2)** connects via WebSocket and calls `join_room` with code `4821`.
4. Server verifies the room, ensures maximum capacity (4 seats), assigns **Seat 1**, and issues Device 2 a `sessionToken`.
5. Server broadcasts `player_joined` and `room_state_updated` to **both** devices in real-time.
6. **Device 1** sends `test_message` with payload and unique `actionId`.
7. Server checks duplicate action protection and broadcasts `test_message_received` to the room; **Device 2 receives it immediately**.
8. **Device 2** simulates sudden network drop (socket disconnect).
9. Server marks Player 2 as `DISCONNECTED`, starts a **30-second countdown**, and notifies Device 1 via `player_disconnected`.
10. **Device 2** reconnects with its `sessionToken`. Server restores Player 2 to **Seat 1** as `CONNECTED` and notifies Device 1 via `player_reconnected`.

---

## 7. What Public WebSocket URL the Android App Will Use

When deploying the backend:

- **Production Deployed Server**:
  - `wss://mobile-card-game.onrender.com`

In the Android app, this will be injected via `BuildConfig.SERVER_URL` or configured in the Settings menu.
