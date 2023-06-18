package com.letsbuildthatapp.kotlinmessenger.data.models

import android.os.Parcelable
import com.google.errorprone.annotations.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class Conversation(
    val id: String = "",
    val unitId: String = "", // id of either person or group
    val secondUnitId: String? = null, // id of the second person
    val type: String = "", // look into the companion object
    val lastMessageId: String = "",
    val date: Long = 0
) : Parcelable {

    companion object {

        const val TYPE_PERSON = "person"
        const val TYPE_GROUP = "group"
    }
}
