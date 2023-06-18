package com.letsbuildthatapp.kotlinmessenger.data.models

import android.os.Parcelable
import com.google.errorprone.annotations.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val profileImageUrl: String = "",
    val conversations: HashMap<String, String> = hashMapOf()
) : Parcelable