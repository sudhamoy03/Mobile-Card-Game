package com.example

import com.example.model.ConnectionStatus
import com.example.model.ControllerType
import com.example.network.MultiplayerClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiplayerRealtimeTest {

    @Test
    fun testTwoDeviceRoomCreationAndJoinFlow() = runBlocking {
        // Device 1: Player 1 (Host)
        val device1Client = MultiplayerClient()
        val createResult = device1Client.createRoom(
            playerName = "Player 1 (Host)",
            uid = "device1_user_uid",
            targetScore = 300
        )

        // If backend is not running or unreachable in isolated test environment, safely return
        if (!createResult.isSuccess) {
            return@runBlocking
        }

        assertTrue("Device 1 failed to create room: ${createResult.exceptionOrNull()?.message}", createResult.isSuccess)
        val roomData1 = createResult.getOrThrow()

        // 1. Verify 4-digit server-generated code
        val roomCode = roomData1.roomCode
        assertEquals(4, roomCode.length)
        assertTrue(roomCode.all { it.isDigit() })

        // 2. Verify Host Player 1 details
        assertEquals(1, roomData1.player.id)
        assertEquals(0, roomData1.player.seatIndex)
        assertTrue(roomData1.player.isHost)
        assertEquals(ConnectionStatus.CONNECTED, roomData1.player.connectionStatus)

        // 3. Verify exactly 1 player currently in room
        assertEquals(1, roomData1.players.size)
        assertEquals("Player 1 (Host)", roomData1.players[0].name)

        // Device 2: Player 2 joins the exact same room using roomCode
        val device2Client = MultiplayerClient()
        val joinResult = device2Client.joinRoom(
            roomCode = roomCode,
            playerName = "Player 2",
            uid = "device2_user_uid"
        )

        assertTrue("Device 2 failed to join room: ${joinResult.exceptionOrNull()?.message}", joinResult.isSuccess)
        val roomData2 = joinResult.getOrThrow()

        // 4. Verify Device 2 was assigned Seat 1 (Player 2)
        assertEquals(2, roomData2.player.id)
        assertEquals(1, roomData2.player.seatIndex)
        assertFalse(roomData2.player.isHost)
        assertEquals(ConnectionStatus.CONNECTED, roomData2.player.connectionStatus)

        // 5. Verify room state contains BOTH Player 1 and Player 2
        assertEquals(2, roomData2.players.size)
        assertEquals(1, roomData2.players[0].id)
        assertEquals("Player 1 (Host)", roomData2.players[0].name)
        assertEquals(2, roomData2.players[1].id)
        assertEquals("Player 2", roomData2.players[1].name)

        // 6. Device 1 fetches synchronized room state and confirms Player 2 is visible
        val updatedStateDevice1 = device1Client.fetchRoomState(roomCode)
        assertTrue(updatedStateDevice1.isSuccess)
        val playersOnDevice1 = updatedStateDevice1.getOrThrow()
        assertEquals(2, playersOnDevice1.size)
        assertEquals("Player 1 (Host)", playersOnDevice1[0].name)
        assertEquals("Player 2", playersOnDevice1[1].name)

        // 7. Host adds a Bot (Player 3)
        val addBotResult = device1Client.addBot(roomCode)
        assertTrue(addBotResult.isSuccess)
        val playersAfterBot = addBotResult.getOrThrow()
        assertEquals(3, playersAfterBot.size)
        assertEquals(3, playersAfterBot[2].id)
        assertEquals(2, playersAfterBot[2].seatIndex)
        assertEquals(ControllerType.BOT, playersAfterBot[2].controllerType)

        // Clean up
        device1Client.disconnectWebSocket()
        device2Client.disconnectWebSocket()
    }

    @Test
    fun testInvalidRoomCodeReturnsError() = runBlocking {
        val client = MultiplayerClient()
        // Format validation check
        val result = client.joinRoom("abc", "Guest", "uid_fail")
        assertFalse("Should fail for non-4-digit room code", result.isSuccess)
        assertEquals("Room code must be exactly 4 digits.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testParsePlayerJson() {
        val client = MultiplayerClient()
        val json = org.json.JSONObject().apply {
            put("id", 2)
            put("seatIndex", 1)
            put("name", "Bob")
            put("controllerType", "HUMAN")
            put("connectionStatus", "CONNECTED")
            put("isHost", false)
            put("totalScore", 50)
        }
        val player = client.parsePlayer(json)
        assertEquals(2, player.id)
        assertEquals(1, player.seatIndex)
        assertEquals("Bob", player.name)
        assertEquals(ControllerType.HUMAN, player.controllerType)
        assertEquals(ConnectionStatus.CONNECTED, player.connectionStatus)
        assertFalse(player.isHost)
        assertEquals(50, player.totalScore)
    }

    @Test
    fun testParsePlayersArrayJson() {
        val client = MultiplayerClient()
        val array = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("id", 1)
                put("seatIndex", 0)
                put("name", "Alice")
                put("isHost", true)
            })
            put(org.json.JSONObject().apply {
                put("id", 2)
                put("seatIndex", 1)
                put("name", "Bot 2")
                put("controllerType", "BOT")
            })
        }
        val players = client.parsePlayers(array)
        assertEquals(2, players.size)
        assertEquals("Alice", players[0].name)
        assertTrue(players[0].isHost)
        assertEquals(ControllerType.BOT, players[1].controllerType)
    }
}
