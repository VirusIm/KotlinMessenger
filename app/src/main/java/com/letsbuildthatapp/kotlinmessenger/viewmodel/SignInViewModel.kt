package com.letsbuildthatapp.kotlinmessenger.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class SignInViewModel : ViewModel() {

    fun signIn(
        email: String,
        password: String,
        listener: (Result<Unit>) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                listener(Result.success(Unit))
            }
            .addOnFailureListener {
                listener(Result.failure(it))
            }
    }
}