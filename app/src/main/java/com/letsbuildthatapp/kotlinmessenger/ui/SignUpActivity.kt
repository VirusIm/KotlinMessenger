package com.letsbuildthatapp.kotlinmessenger.ui

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.databinding.ActivitySignUpBinding
import com.letsbuildthatapp.kotlinmessenger.viewmodel.SignUpViewModel

class SignUpActivity : AppCompatActivity(R.layout.activity_sign_up) {

    private val binding: ActivitySignUpBinding by viewBinding()
    private val viewModel: SignUpViewModel by viewModels()

    private var selectedPhotoUri: Uri? = null
    private val pickPhotoContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                selectedPhotoUri = it

                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(
                        this.contentResolver,
                        selectedPhotoUri
                    )
                } else {
                    val source = ImageDecoder.createSource(this.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }

                binding.chosenPhoto.setImageBitmap(bitmap)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnSignUp.setOnClickListener {
            val username = binding.textFieldUsername.text.toString()
            val email = binding.textFieldEmail.text.toString()
            val password = binding.textFieldPassword.text.toString()

            if (username.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_username), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedPhotoUri == null) {
                Toast.makeText(this, getString(R.string.choose_a_photo), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.signUp(
                username = username,
                email = email,
                password = password,
                picture = selectedPhotoUri!!
            ) {
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
        binding.btnSignIn.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }
        binding.btnChoosePhoto.setOnClickListener {
            pickPhotoContract.launch("image/*")
        }
    }
}