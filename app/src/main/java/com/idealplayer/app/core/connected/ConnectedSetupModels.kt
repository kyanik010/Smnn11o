package com.idealplayer.app.core.connected

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val CONNECTED_IDEALPLAYER_PAYLOAD_SOURCE = "connected-idealplayer"
const val CONNECTED_IDEALPLAYER_PAYLOAD_VERSION = 1
const val CONNECTED_PAIRING_CODE_LENGTH = 8
const val CONNECTED_PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
const val CONNECTED_SETUP_MAX_PLAYLIST_BYTES = 5L * 1024L * 1024L

@Serializable
data class ConnectedPairingResponse(
    val status: String,
    val payload: JsonObject? = null
)

@Serializable
data class ConnectedSetupPayload(
    val version: Int,
    val source: String,
    val pairingCode: String,
    val playlist: ConnectedPlaylistInfo
)

@Serializable
data class ConnectedPlaylistInfo(
    val name: String,
    val type: String,
    val epgUrl: String = "",
    val rememberOnStart: Boolean = true,
    val epgAutoSync: Boolean = true,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val fileContent: String = ""
)
