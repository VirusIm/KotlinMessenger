package com.letsbuildthatapp.kotlinmessenger.ui

import android.content.Context
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
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.databinding.ActivityProfileBinding
import com.letsbuildthatapp.kotlinmessenger.viewmodel.ProfileViewModel

class ProfileActivity : AppCompatActivity(R.layout.activity_profile) {

    private val extraEditingMode by lazy {
        intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
    }
    private val extraUserId by lazy {
        intent.getStringExtra(EXTRA_USER_ID)
    }

    private val binding: ActivityProfileBinding by viewBinding()
    private val viewModel: ProfileViewModel by viewModels()

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

        with(binding) {
            btnBack.setOnClickListener {
                finish()
            }

            if (extraEditingMode) {
                textFieldEmail.isVisible = false

                btnSave.setOnClickListener {
                    if (textFieldCurrentPassword.text.toString() != textFieldConfirmPassword.text.toString()) {
                        Toast.makeText(
                            this@ProfileActivity,
                            getString(R.string.passwords_do_not_match),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    viewModel.updateProfile(
                        newUsername = textFieldUsername.text.toString(),
                        newPhoneNumber = textFieldPhoneNumber.text.toString(),
                        newPhoto = selectedPhotoUri,
                        oldPassword = textFieldCurrentPassword.text.toString(),
                        newPassword = textFieldNewPassword.text.toString()
                    ) { Toast.makeText(this@ProfileActivity, it, Toast.LENGTH_SHORT).show() }
                }
                btnChoosePhoto.setOnClickListener {
                    pickPhotoContract.launch("image/*")
                }
            } else {
                textFieldCurrentPassword.isVisible = false
                textFieldNewPassword.isVisible = false
                textFieldConfirmPassword.isVisible = false
                btnSave.isVisible = false
                textPickPhoto.isVisible = false

                textFieldEmail.isEnabled = false
                textFieldPhoneNumber.isEnabled = false
                textFieldUsername.isEnabled = false
            }
        }
        viewModel.getUserInfo(extraUserId ?: FirebaseAuth.getInstance().uid!!) {
            with(binding) {
                chosenPhoto.load(it.profileImageUrl)
                textFieldEmail.setText(it.email)
                textFieldUsername.setText(it.username)

                if (it.phoneNumber.isNotEmpty() || extraEditingMode) {
                    textFieldPhoneNumber.setText(it.phoneNumber)
                } else {
                    textFieldPhoneNumber.isVisible = false
                }
            }
        }
    }

    companion object {

        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_EDIT_MODE = "edit_mode"

        private fun getIntent(
            context: Context,
            userId: String?,
            editMode: Boolean
        ) = Intent(context, ProfileActivity::class.java).apply {
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_EDIT_MODE, editMode)
        }

        fun getEditingIntent(context: Context) =
            getIntent(context, null, true)

        fun getProfileIntent(context: Context, userId: String) =
            getIntent(context, userId, false)
    }
}