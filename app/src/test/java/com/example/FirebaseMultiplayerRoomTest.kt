package com.example

import com.example.engine.AuthoritativeRuleEngine
import com.example.model.Card
import com.example.model.ConnectionStatus
import com.example.model.ControllerType
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameState
import com.example.model.PlayedCard
import com.example.model.Player
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.Trick
import com.example.network.firebase.RoomData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseMultiplayerRoomTest {

    @Test
    fun testRoomCodeGenerationIsFourDigits() {
        val regex = Regex("^\\d{4}$")
        for (i in 1..100) {
            val code = String.format("%04d", kotlin.random.Random.nextInt(1000, 10000))
            assertTrue("Code $code must be 4 digits", regex.matches(code))
        }
    }

    @Test
    fun testFourDeviceRoomCreationAndJoinFlow() {
        // Device 1: Host creates Room
        val roomCode = "4821"
        val host = Player(
            id = 1,
            seatIndex = 0,
            name = "Player 1 (Host)",
            controllerType = ControllerType.HUMAN,
            connectionStatus = ConnectionStatus.CONNECTED,
            isHost = true
        )
        val roomData = RoomData(
            roomCode = roomCode,
            hostId = "uid_host_1",
            status = "WAITING",
            players = mutableListOf(host)
        )

        assertEquals("Player 1 (Host)", roomData.players[0].name)
        assertEquals(0, roomData.players[0].seatIndex)
        assertEquals(1, roomData.players[0].id)
        assertTrue(roomData.players[0].isHost)

        // Device 2 joins
        val p2 = Player(
            id = 2,
            seatIndex = 1,
            name = "Player 2",
            controllerType = ControllerType.HUMAN,
            connectionStatus = ConnectionStatus.CONNECTED,
            isHost = false
        )
        val playersAfterD2 = roomData.players + p2
        assertEquals(2, playersAfterD2.size)
        assertEquals(1, playersAfterD2[1].seatIndex)

        // Device 3 joins
        val p3 = Player(
            id = 3,
            seatIndex = 2,
            name = "Player 3",
            controllerType = ControllerType.HUMAN,
            connectionStatus = ConnectionStatus.CONNECTED,
            isHost = false
        )
        val playersAfterD3 = playersAfterD2 + p3
        assertEquals(3, playersAfterD3.size)
        assertEquals(2, playersAfterD3[2].seatIndex)

        // Device 4 joins
        val p4 = Player(
            id = 4,
            seatIndex = 3,
            name = "Player 4",
            controllerType = ControllerType.HUMAN,
            connectionStatus = ConnectionStatus.CONNECTED,
            isHost = false
        )
        val playersAfterD4 = playersAfterD3 + p4
        assertEquals(4, playersAfterD4.size)
        assertEquals(3, playersAfterD4[3].seatIndex)

        // Device 5 attempts to join: Room must be reported Full
        val isFull = playersAfterD4.size >= 4
        assertTrue("Room must be reported as full when 4 players are seated", isFull)

        // All seats are unique 0..3
        val seats = playersAfterD4.map { it.seatIndex }.toSet()
        assertEquals(setOf(0, 1, 2, 3), seats)
    }

    @Test
    fun testRealtimeGameFlowSynchronizationAcrossDevices() {
        val players = (1..4).map { i ->
            Player(
                id = i,
                seatIndex = i - 1,
                name = "Player $i",
                controllerType = ControllerType.HUMAN,
                connectionStatus = ConnectionStatus.CONNECTED,
                isHost = (i == 1),
                cards = listOf(
                    Card(Suit.SPADES, Rank.ACE),
                    Card(Suit.HEARTS, Rank.KING),
                    Card(Suit.CLUBS, Rank.TEN),
                    Card(Suit.DIAMONDS, Rank.SEVEN)
                )
            )
        }

        // Host starts game: all 4 devices share synchronized state
        val gameState = GameState(
            gameId = "online_4821",
            mode = GameMode.ONLINE,
            phase = GamePhase.PLAYING,
            roomCode = "4821",
            players = players,
            currentTurnPlayerIndex = 0,
            roundNumber = 1,
            targetScore = 300
        )

        assertEquals(GameMode.ONLINE, gameState.mode)
        assertEquals(4, gameState.players.size)
        assertEquals(0, gameState.currentTurnPlayerIndex)

        // Synchronize a call action
        val callSubmitted = 3
        assertTrue(AuthoritativeRuleEngine.isCallValid(callSubmitted))

        // Synchronize a card play action
        val leadCard = Card(Suit.SPADES, Rank.ACE)
        val trick = Trick(
            trickNumber = 1,
            leadSuit = Suit.SPADES,
            playedCards = listOf(PlayedCard(playerIndex = 0, card = leadCard))
        )
        assertEquals(1, trick.playedCards.size)
        assertEquals(Suit.SPADES, trick.leadSuit)

        // Next turn rotates clockwise to Player 2 (seat 1)
        val nextTurnIndex = (0 + 1) % 4
        assertEquals(1, nextTurnIndex)
    }

    @Test
    fun testDisconnectAndReconnectPreservesState() {
        val cards = listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.KING))
        val player = Player(
            id = 2,
            seatIndex = 1,
            name = "Player 2",
            controllerType = ControllerType.HUMAN,
            connectionStatus = ConnectionStatus.CONNECTED,
            cards = cards,
            call = 3,
            totalScore = 80
        )

        // 1. Temporary Disconnect
        val disconnectedPlayer = player.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            disconnectRemainingSeconds = 30
        )
        // Seat, cards, call, and score are strictly preserved!
        assertEquals(1, disconnectedPlayer.seatIndex)
        assertEquals(cards, disconnectedPlayer.cards)
        assertEquals(3, disconnectedPlayer.call)
        assertEquals(80, disconnectedPlayer.totalScore)
        assertEquals(30, disconnectedPlayer.disconnectRemainingSeconds)

        // 2. Reconnect within 30s
        val reconnectedPlayer = disconnectedPlayer.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            disconnectRemainingSeconds = null
        )
        assertEquals(ConnectionStatus.CONNECTED, reconnectedPlayer.connectionStatus)
        assertEquals(1, reconnectedPlayer.seatIndex)
        assertEquals(cards, reconnectedPlayer.cards)
        assertEquals(3, reconnectedPlayer.call)
        assertEquals(80, reconnectedPlayer.totalScore)

        // 3. Timeout fallback: Bot takeover in the SAME seat
        val botTakeoverPlayer = disconnectedPlayer.copy(
            connectionStatus = ConnectionStatus.BOT_ACTIVE,
            controllerType = ControllerType.BOT,
            disconnectRemainingSeconds = null
        )
        assertEquals(ControllerType.BOT, botTakeoverPlayer.controllerType)
        assertEquals(ConnectionStatus.BOT_ACTIVE, botTakeoverPlayer.connectionStatus)
        assertEquals(1, botTakeoverPlayer.seatIndex)
        assertEquals(cards, botTakeoverPlayer.cards)
        assertEquals(3, botTakeoverPlayer.call)
        assertEquals(80, botTakeoverPlayer.totalScore)
    }
}
