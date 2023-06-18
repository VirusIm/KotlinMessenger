package com.letsbuildthatapp.kotlinmessenger.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.databinding.ActivitySignInBinding
import com.letsbuildthatapp.kotlinmessenger.viewmodel.SignInViewModel

class SignInActivity : AppCompatActivity(R.layout.activity_sign_in) {

    private val binding: ActivitySignInBinding by viewBinding()
    private val viewModel: SignInViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnSignIn.setOnClickListener {
            val email = binding.textFieldEmail.text.toString()
            val password = binding.textFieldPassword.text.toString()

            if (email.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.signIn(email, password) {
                if (it.isSuccess) {
                    startActivity(
                        Intent(this, MessagesListActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                } else {
                    Toast.makeText(this, it.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnSignUp.setOnClickListener {
            finish()
        }
    }
}