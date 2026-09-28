export enum LogLevel {
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
  DEBUG = 'DEBUG'
}

class Logger {
  private formatTime(): string {
    return new Date().toISOString();
  }

  info(module: string, message: string, meta?: any): void {
    console.log(
      `[${this.formatTime()}] [INFO] [${module}] ${message}`,
      meta !== undefined ? JSON.stringify(meta) : ''
    );
  }

  warn(module: string, message: string, meta?: any): void {
    console.warn(
      `[${this.formatTime()}] [WARN] [${module}] ${message}`,
      meta !== undefined ? JSON.stringify(meta) : ''
    );
  }

  error(module: string, message: string, error?: any): void {
    console.error(
      `[${this.formatTime()}] [ERROR] [${module}] ${message}`,
      error?.stack || (error !== undefined ? JSON.stringify(error) : '')
    );
  }

  debug(module: string, message: string, meta?: any): void {
    if (process.env.NODE_ENV !== 'production') {
      console.log(
        `[${this.formatTime()}] [DEBUG] [${module}] ${message}`,
        meta !== undefined ? JSON.stringify(meta) : ''
      );
    }
  }
}

export const logger = new Logger();
