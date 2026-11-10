package com.idealplayer.app.core.common

object Constants {
    const val DEFAULT_SEEK_FORWARD_MS = 10_000L
    const val DEFAULT_SEEK_BACKWARD_MS = 10_000L
    const val DEFAULT_BUFFER_MS = 30_000
    const val MIN_BUFFER_MS = 15_000
    const val MAX_BUFFER_MS = 60_000
    const val RECONNECT_DELAY_MS = 3_000L
    const val MAX_RECONNECT_ATTEMPTS = 5
    const val SEARCH_DEBOUNCE_MS = 200L
    const val CONTROLLER_AUTO_HIDE_MS = 5_000L
    const val OVERSCAN_PADDING_DP = 48
    const val TV_CARD_WIDTH_DP = 180
    const val TV_CARD_HEIGHT_DP = 270
    const val MOBILE_CARD_WIDTH_DP = 110
    const val MOBILE_CARD_HEIGHT_DP = 165
    const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
    const val TMDB_POSTER_SIZE = "w500"
    const val TMDB_BACKDROP_SIZE = "w1280"
    const val XTREAM_API_PATH = "/player_api.php"
}
