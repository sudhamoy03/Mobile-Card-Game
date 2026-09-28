package com.example.data

import com.example.model.Card
import com.example.model.ConnectionStatus
import com.example.model.ControllerType
import com.example.model.PlayedCard
import com.example.model.Player
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.Trick
import org.json.JSONArray
import org.json.JSONObject

object GameSerializer {

    fun serializeCard(card: Card): JSONObject {
        return JSONObject().apply {
            put("suit", card.suit.name)
            put("rank", card.rank.name)
        }
    }

    fun deserializeCard(json: JSONObject): Card {
        val suit = Suit.valueOf(json.getString("suit"))
        val rank = Rank.valueOf(json.getString("rank"))
        return Card(suit, rank)
    }

    fun serializeCards(cards: List<Card>): String {
        val array = JSONArray()
        cards.forEach { array.put(serializeCard(it)) }
        return array.toString()
    }

    fun deserializeCards(jsonString: String): List<Card> {
        val list = mutableListOf<Card>()
        if (jsonString.isBlank()) return list
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            list.add(deserializeCard(array.getJSONObject(i)))
        }
        return list
    }

    fun serializePlayer(player: Player): JSONObject {
        return JSONObject().apply {
            put("id", player.id)
            put("seatIndex", player.seatIndex)
            put("name", player.name)
            put("controllerType", player.controllerType.name)
            put("connectionStatus", player.connectionStatus.name)
            put("cards", JSONArray().apply { player.cards.forEach { put(serializeCard(it)) } })
            put("call", player.call ?: JSONObject.NULL)
            put("tricksWon", player.tricksWon)
            put("roundScore", player.roundScore)
            put("totalScore", player.totalScore)
            put("failedTurnAttempts", player.failedTurnAttempts)
            put("hasModifiedCall", player.hasModifiedCall)
            put("isHost", player.isHost)
        }
    }

    fun deserializePlayer(json: JSONObject): Player {
        val cardsList = mutableListOf<Card>()
        val cardsArray = json.optJSONArray("cards")
        if (cardsArray != null) {
            for (i in 0 until cardsArray.length()) {
                cardsList.add(deserializeCard(cardsArray.getJSONObject(i)))
            }
        }

        return Player(
            id = json.getInt("id"),
            seatIndex = json.getInt("seatIndex"),
            name = json.getString("name"),
            controllerType = ControllerType.valueOf(json.getString("controllerType")),
            connectionStatus = ConnectionStatus.valueOf(json.getString("connectionStatus")),
            cards = cardsList,
            call = if (json.isNull("call")) null else json.getInt("call"),
            tricksWon = json.getInt("tricksWon"),
            roundScore = json.getInt("roundScore"),
            totalScore = json.getInt("totalScore"),
            failedTurnAttempts = json.optInt("failedTurnAttempts", 0),
            hasModifiedCall = json.optBoolean("hasModifiedCall", false),
            isHost = json.optBoolean("isHost", false)
        )
    }

    fun serializePlayers(players: List<Player>): String {
        val array = JSONArray()
        players.forEach { array.put(serializePlayer(it)) }
        return array.toString()
    }

    fun deserializePlayers(jsonString: String): List<Player> {
        val list = mutableListOf<Player>()
        if (jsonString.isBlank()) return list
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            list.add(deserializePlayer(array.getJSONObject(i)))
        }
        return list
    }

    fun serializeTrick(trick: Trick): JSONObject {
        return JSONObject().apply {
            put("trickNumber", trick.trickNumber)
            put("leadSuit", trick.leadSuit?.name ?: JSONObject.NULL)
            put("winnerIndex", trick.winnerIndex ?: JSONObject.NULL)
            val playedArray = JSONArray()
            trick.playedCards.forEach { played ->
                playedArray.put(JSONObject().apply {
                    put("playerIndex", played.playerIndex)
                    put("card", serializeCard(played.card))
                    put("timestamp", played.timestamp)
                })
            }
            put("playedCards", playedArray)
        }
    }

    fun deserializeTrick(json: JSONObject): Trick {
        val trickNum = json.getInt("trickNumber")
        val leadSuit = if (json.isNull("leadSuit")) null else Suit.valueOf(json.getString("leadSuit"))
        val winnerIndex = if (json.isNull("winnerIndex")) null else json.getInt("winnerIndex")
        val playedList = mutableListOf<PlayedCard>()
        val playedArray = json.optJSONArray("playedCards")
        if (playedArray != null) {
            for (i in 0 until playedArray.length()) {
                val item = playedArray.getJSONObject(i)
                playedList.add(
                    PlayedCard(
                        playerIndex = item.getInt("playerIndex"),
                        card = deserializeCard(item.getJSONObject("card")),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
        return Trick(trickNum, leadSuit, playedList, winnerIndex)
    }

    fun serializeTricks(tricks: List<Trick>): String {
        val array = JSONArray()
        tricks.forEach { array.put(serializeTrick(it)) }
        return array.toString()
    }

    fun deserializeTricks(jsonString: String): List<Trick> {
        val list = mutableListOf<Trick>()
        if (jsonString.isBlank()) return list
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            list.add(deserializeTrick(array.getJSONObject(i)))
        }
        return list
    }
}
