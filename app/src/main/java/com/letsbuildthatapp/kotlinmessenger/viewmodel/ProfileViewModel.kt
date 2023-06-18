package com.letsbuildthatapp.kotlinmessenger.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.letsbuildthatapp.kotlinmessenger.data.models.User
import com.letsbuildthatapp.kotlinmessenger.utils.addSingleValueListener
import com.letsbuildthatapp.kotlinmessenger.utils.getMe
import com.letsbuildthatapp.kotlinmessenger.utils.getProfilePicture
import com.letsbuildthatapp.kotlinmessenger.utils.getUser
import java.util.UUID

class ProfileViewModel : ViewModel() {

    private var listener: ValueEventListener? = null
    private var userCache: User? = null

    fun getUserInfo(userId: String, onGot: (User) -> Unit) {
        FirebaseDatabase.getInstance()
            .getUser(userId)
            .addSingleValueListener {
                it.getValue(User::class.java)?.let { user ->
                    onGot(user)
                    userCache = user
                }

                FirebaseDatabase.getInstance()
                    .getUser(userId)
                    .removeEventListener(listener!!)
            }
            .also { listener = it }
    }

    fun updateProfile(
        newUsername: String,
        newPhoneNumber: String,
        newPhoto: Uri?,
        oldPassword: String,
        newPassword: String,
        onError: (String) -> Unit
    ) {
        if (newUsername.isNotBlank() && userCache?.username != newUsername) {
            FirebaseDatabase.getInstance()
                .getMe()
                .child("username")
                .setValue(newUsername)
                .addOnSuccessListener { userCache = userCache?.copy(username = newUsername) }
                .addOnFailureListener { onError(it.message!!) }
        }
        if (newPhoneNumber.isNotBlank() && userCache?.phoneNumber != newPhoneNumber) {
            FirebaseDatabase.getInstance()
                .getMe()
                .child("phoneNumber")
                .setValue(newPhoneNumber)
                .addOnSuccessListener { userCache = userCache?.copy(phoneNumber = newPhoneNumber) }
                .addOnFailureListener { onError(it.message!!) }
        }
        if (newPhoto != null && userCache != null) {
            val pictureRef = FirebaseStorage.getInstance()
                .getProfilePicture(UUID.randomUUID().toString())

            pictureRef
                .putFile(newPhoto)
                .addOnSuccessListener {
                    FirebaseStorage.getInstance()
                        .getReferenceFromUrl(userCache!!.profileImageUrl)
                        .delete()
                    pictureRef.downloadUrl.addOnSuccessListener { newUri ->
                        FirebaseDatabase.getInstance()
                            .getMe()
                            .child("profileImageUrl")
                            .setValue(newUri.toString())
                        userCache = userCache?.copy(profileImageUrl = newUri.toString())
                    }.addOnFailureListener { onError(it.message!!) }
                }
                .addOnFailureListener { onError(it.message!!) }
        }
        if (oldPassword.isNotBlank() && newPassword.isNotBlank() && userCache != null) {
            FirebaseAuth.getInstance()
                .currentUser!!
                .reauthenticate(EmailAuthProvider.getCredential(userCache!!.email, oldPassword))
                .addOnSuccessListener {
                    FirebaseAuth.getInstance().currentUser!!.updatePassword(newPassword)
                }
                .addOnFailureListener { onError(it.message!!) }
        }
    }
}