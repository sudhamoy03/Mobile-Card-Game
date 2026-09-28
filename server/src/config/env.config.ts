import dotenv from 'dotenv';
dotenv.config();

export interface ServerConfig {
  port: number;
  host: string;
  nodeEnv: string;
  corsOrigin: string;
  pingIntervalMs: number;
  pingTimeoutMs: number;
  reconnectWindowSeconds: number;
  authMode: 'firebase' | 'dev';
}

const resolvedPort = (() => {
  if (process.env.GAME_SERVER_PORT) return parseInt(process.env.GAME_SERVER_PORT, 10);
  if (process.env.MULTIPLAYER_PORT) return parseInt(process.env.MULTIPLAYER_PORT, 10);
  // Avoid conflict with Android emulator / preview proxy port 8080
  if (process.env.PORT && process.env.PORT !== '8080') return parseInt(process.env.PORT, 10);
  return 3000;
})();

export const serverConfig: ServerConfig = {
  port: resolvedPort,
  host: process.env.HOST || '0.0.0.0',
  nodeEnv: process.env.NODE_ENV || 'development',
  corsOrigin: process.env.CORS_ORIGIN || '*',
  pingIntervalMs: parseInt(process.env.PING_INTERVAL_MS || '10000', 10),
  pingTimeoutMs: parseInt(process.env.PING_TIMEOUT_MS || '5000', 10),
  reconnectWindowSeconds: parseInt(process.env.RECONNECT_WINDOW_SECONDS || '30', 10),
  authMode: (process.env.AUTH_MODE === 'firebase' ? 'firebase' : 'dev') as 'firebase' | 'dev'
};
