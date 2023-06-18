package com.letsbuildthatapp.kotlinmessenger.data.models

import com.google.errorprone.annotations.Keep

@Keep
data class Message(
    val id: String = "",

    val text: String? = null,
    var attachment: String? = null,
    val edited: Boolean = false,
    val system: Boolean = false,

    val fromId: String = "",
    val timestamp: Any? = null
)