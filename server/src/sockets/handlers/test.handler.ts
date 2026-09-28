import { Socket, Server } from 'socket.io';
import { roomService } from '../../services/room.service';
import { toPublicPlayer } from '../../types/player.types';
import { TestMessagePayload, TestMessageBroadcastEvent } from '../../types/events.types';
import { logger } from '../../utils/logger';

export function registerTestHandlers(io: Server, socket: Socket): void {
  /**
   * Event: test_message
   * Real-time messaging test: Player 1 sends TEST_MESSAGE, server broadcasts to everyone in room,
   * Player 2 receives it immediately.
   */
  socket.on('test_message', (payload: TestMessagePayload, callback?: (res: any) => void) => {
    try {
      const { roomCode, message, actionId } = payload;

      // 1. Duplicate action protection
      if (roomService.isActionProcessed(actionId)) {
        logger.warn('Socket:Test', `Duplicate test_message action ignored: ${actionId}`);
        if (callback) callback({ success: true, duplicate: true });
        return;
      }
      roomService.markActionProcessed(actionId);

      // 2. Validate player and room
      const mapping = roomService.getPlayerBySocket(socket.id);
      if (!mapping || mapping.room.roomCode !== roomCode) {
        throw new Error('Unauthorized or player not in specified room');
      }

      const { player } = mapping;
      logger.info(
        'Socket:Test',
        `TEST_MESSAGE received from ${player.name} [seat ${player.seatIndex}] in room ${roomCode}: "${message}"`
      );

      // 3. Construct broadcast event
      const broadcastEvent: TestMessageBroadcastEvent = {
        fromPlayer: toPublicPlayer(player),
        message: message.trim(),
        actionId,
        timestamp: Date.now()
      };

      // 4. Server broadcasts immediately to every player in the room
      io.in(roomCode).emit('test_message_received', broadcastEvent);

      if (callback) {
        callback({ success: true, timestamp: broadcastEvent.timestamp });
      }
    } catch (error: any) {
      logger.error('Socket:Test', 'test_message error', error);
      const errorResponse = { code: 'TEST_MESSAGE_FAILED', message: error.message };
      socket.emit('error', errorResponse);
      if (callback) callback({ success: false, error: errorResponse });
    }
  });
}
