package com.letsbuildthatapp.kotlinmessenger.data.models

import com.google.errorprone.annotations.Keep

@Keep
data class Group(
    val id: String = "",
    val name: String = "",
    val photo: String = "",
    val members: Map<String, String> = mapOf(),
    val conversation: String = ""
)
