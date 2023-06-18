package com.letsbuildthatapp.kotlinmessenger.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.letsbuildthatapp.kotlinmessenger.data.models.User
import com.letsbuildthatapp.kotlinmessenger.utils.getProfilePicture
import com.letsbuildthatapp.kotlinmessenger.utils.getUser
import java.util.UUID

class SignUpViewModel : ViewModel() {

    fun signUp(
        username: String,
        email: String,
        password: String,
        picture: Uri,
        listener: (Result<Unit>) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val pictureRef = FirebaseStorage.getInstance()
                    .getProfilePicture(UUID.randomUUID().toString())

                pictureRef.putFile(picture)
                    .addOnSuccessListener {
                        pictureRef.downloadUrl.addOnSuccessListener { pictureUri ->
                            val uid = FirebaseAuth.getInstance().uid ?: ""
                            val userRef = FirebaseDatabase.getInstance().getUser(uid)
                            val user = User(
                                uid = uid,
                                username = username,
                                email = email,
                                profileImageUrl = pictureUri.toString()
                            )

                            userRef.setValue(user)
                                .addOnSuccessListener {
                                    listener(Result.success(Unit))
                                }
                                .addOnFailureListener { exception ->
                                    listener(Result.failure(exception))
                                }
                        }
                    }
                    .addOnFailureListener { exception ->
                        listener(Result.failure(exception))
                    }
            }
            .addOnFailureListener { exception ->
                listener(Result.failure(exception))
            }
    }
}