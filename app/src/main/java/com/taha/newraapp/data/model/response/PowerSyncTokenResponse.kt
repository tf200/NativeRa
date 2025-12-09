package com.taha.newraapp.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from /powersync/authenticate endpoint
 */
@Serializable
data class PowerSyncTokenResponse(
    val token: String,
    @SerialName("powersync_url")
    val powersyncUrl: String
)
