package com.letsbuildthatapp.kotlinmessenger.ui

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import by.kirich1409.viewbindingdelegate.viewBinding
import coil.load
import com.elveum.elementadapter.context
import com.elveum.elementadapter.simpleAdapter
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.data.models.User
import com.letsbuildthatapp.kotlinmessenger.databinding.ActivityUserListBinding
import com.letsbuildthatapp.kotlinmessenger.databinding.UserListItemBinding
import com.letsbuildthatapp.kotlinmessenger.viewmodel.UserListViewModel
import okhttp3.internal.toImmutableList

class UserListActivity : AppCompatActivity(R.layout.activity_user_list) {

    private val extraReturnResult by lazy {
        intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)
    }
    private val extraCreateGroup by lazy {
        intent.getBooleanExtra(EXTRA_CREATE_GROUP, false)
    }
    private val extraGroupId by lazy {
        intent.getStringExtra(EXTRA_GROUP_ID)
    }
    private val extraGroupName by lazy {
        intent.getStringExtra(EXTRA_GROUP_NAME)
    }
    private val extraExcludedUsers by lazy {
        intent.getStringArrayListExtra(EXTRA_EXCLUDED_USERS)
    }

    private val binding: ActivityUserListBinding by viewBinding()
    private val viewModel: UserListViewModel by viewModels {
        UserListViewModel.Factory(
            extraGroupId,
            extraExcludedUsers
        )
    }

    private val selectedUsers = mutableListOf<User>()
    private val adapter = simpleAdapter<User, UserListItemBinding> {
        bind {
            picture.shapeAppearanceModel = picture.shapeAppearanceModel
                .withCornerSize { rect -> rect.height() / 2 }
            picture.load(it.profileImageUrl)
            username.text = it.username

            root.isSelected = it in selectedUsers
        }
        listeners {
            root.onClick {
                when {
                    selectedUsers.isNotEmpty() -> {
                        if (selectedUsers.contains(it)) {
                            selectedUsers.remove(it)
                        } else {
                            selectedUsers.add(it)
                        }
                        root.isSelected = selectedUsers.contains(it)

                        updateToolbar()
                    }

                    extraReturnResult -> {
                        setResult(RESULT_OK, Intent().apply {
                            putExtra(EXTRA_USER_ID, it.uid)
                        })
                        finish()
                    }

                    it in selectedUsers -> {
                        root.isSelected = false
                        selectedUsers.remove(it)

                        updateToolbar()
                    }

                    else -> {
                        startActivity(
                            ProfileActivity.getProfileIntent(
                                this@UserListActivity,
                                it.uid
                            )
                        )
                    }
                }
            }
            root.onLongClick {
                if (extraCreateGroup || extraGroupId != null) {
                    root.isSelected = !root.isSelected

                    if (it in selectedUsers) {
                        selectedUsers.remove(it)
                    } else {
                        selectedUsers.add(it)
                    }

                    updateToolbar()
                    true
                } else false
            }
        }
        areItemsSame = { old, new -> old.uid == new.uid }
        areContentsSame = { old, new ->
            old.profileImageUrl == new.profileImageUrl
                    && old.username == new.username
        }
    }

    private val pickUserContract = registerForActivityResult(PickUser()) { user ->
        if (user != null) {
            viewModel.addUser(user, this)
        }
    }

    private var selectedPhotoUri: Uri? = null
    private val pickPhotoContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                if (extraCreateGroup) {
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

                    binding.picture.setImageBitmap(bitmap)
                } else {
                    viewModel.updateGroupPhoto(it)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        viewModel.users.observe(this) {
            adapter.submitList(it)
        }
        viewModel.groupPhoto.observe(this) {
            binding.picture.load(it) {
                val placeholder = ColorDrawable(
                    ContextCompat.getColor(
                        this@UserListActivity,
                        R.color.placeholder
                    )
                )
                placeholder(placeholder)
                error(placeholder)
                crossfade(true)
            }
        }

        with(binding) {
            rvUsersList.adapter = adapter

            if (extraCreateGroup) {
                toolbarTitle.apply {
                    text = null
                    isFocusableInTouchMode = true
                }
                btnDone.apply {
                    isVisible = true
                    setOnClickListener {
                        viewModel.createGroup(
                            name = toolbarTitle.text.toString(),
                            photo = selectedPhotoUri,
                            members = selectedUsers.map { it.uid }
                        ) { conversation ->
                            startActivity(
                                ChatActivity.getIntent(
                                    this@UserListActivity,
                                    conversation
                                )
                            )
                            finish()
                        }
                    }
                }
                btnSearch.isVisible = false
            }

            btnBack.setOnClickListener {
                when {
                    textFieldSearch.isVisible -> {
                        textFieldSearch.text = null
                        textFieldSearch.isVisible = false
                        toolbarTitle.isVisible = true
                        btnSearch.isVisible = true
                        picture.isVisible = extraGroupId != null
                    }

                    toolbarTitle.isFocusableInTouchMode -> {
                        toolbarTitle.isFocusableInTouchMode = false
                        btnSearch.isVisible = true
                        picture.isVisible = extraGroupId != null || extraCreateGroup
                        btnAdd.isVisible = extraGroupId != null
                        btnDone.isVisible = false

                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)

                    }

                    selectedUsers.isNotEmpty() -> {
                        selectedUsers.clear()
                        updateToolbar()
                        adapter.notifyDataSetChanged()
                    }

                    else -> finish()
                }
            }
            btnSearch.setOnClickListener {
                textFieldSearch.isVisible = true
                toolbarTitle.isVisible = false
                btnSearch.isVisible = false
                picture.isVisible = false

                textFieldSearch.requestFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(textFieldSearch, InputMethodManager.SHOW_IMPLICIT)
            }
            btnDelete.setOnClickListener {
                viewModel.deleteUsers(selectedUsers.toImmutableList(), context())
                selectedUsers.clear()
                updateToolbar()
            }
            picture.apply {
                isVisible = extraGroupId != null || extraCreateGroup
                shapeAppearanceModel = shapeAppearanceModel
                    .withCornerSize { rect -> rect.height() / 2 }
                setOnClickListener {
                    pickPhotoContract.launch("image/*")
                }
            }
            if (extraCreateGroup || extraGroupId != null) toolbarTitle.apply {
                gravity = Gravity.CENTER_VERTICAL
                updatePadding(left = (10 * resources.displayMetrics.density).toInt())
            }

            if (extraGroupId != null) {
                btnAdd
                    .also { it.isVisible = true }
                    .setOnClickListener {
                        pickUserContract.launch(viewModel.users.value?.map { it.uid }
                            ?: emptyList())
                    }
                toolbarTitle.setOnLongClickListener {
                    if (it.isFocusableInTouchMode) {
                        false
                    } else {
                        btnSearch.isVisible = false
                        btnAdd.isVisible = false
                        picture.isVisible = false

                        it.isFocusableInTouchMode = true
                        it.requestFocus()

                        val manager =
                            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        manager.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)

                        btnDone.isVisible = true
                        btnDone.setOnClickListener { _ ->
                            val newName = toolbarTitle.text.toString()

                            if (newName.isNotBlank()) {
                                it.isFocusableInTouchMode = false
                                it.clearFocus()

                                btnSearch.isVisible = true
                                picture.isVisible = extraGroupId != null
                                btnAdd.isVisible = extraGroupId != null
                                btnDone.isVisible = false

                                manager.hideSoftInputFromWindow(
                                    it.windowToken,
                                    InputMethodManager.HIDE_IMPLICIT_ONLY
                                )

                                viewModel.updateGroupName(newName.trim())
                            }
                        }

                        true
                    }
                }
            }
            textFieldSearch.doAfterTextChanged {
                viewModel.search(it.toString())
            }
            extraGroupName?.let(toolbarTitle::setText)
        }
    }

    private fun updateToolbar() {
        if (extraGroupId != null) with(binding) {
            btnSearch.isVisible = selectedUsers.isEmpty()
            btnAdd.isVisible = selectedUsers.isEmpty()
            btnDelete.isVisible = selectedUsers.isNotEmpty()

            if (selectedUsers.isEmpty()) {
                textFieldSearch.text = null
                toolbarTitle.updatePadding(
                    left = resources.getDimensionPixelSize(R.dimen.toolbar_height),
                )
            } else {
                textFieldSearch.isVisible = false
                toolbarTitle.updatePadding(left = 0)
            }
        }
    }

    class PickUser : ActivityResultContract<List<String>, String?>() {
        //                                        ^- excluded users

        override fun createIntent(context: Context, input: List<String>): Intent {
            return getPicker(context, input)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): String? {
            if (resultCode != RESULT_OK) return null
            return intent?.getStringExtra(EXTRA_USER_ID)
        }
    }

    companion object {

        private const val EXTRA_RETURN_RESULT = "return_result"
        private const val EXTRA_GROUP_ID = "group_id"
        private const val EXTRA_GROUP_NAME = "group_name"
        private const val EXTRA_CREATE_GROUP = "create_group"
        private const val EXTRA_EXCLUDED_USERS = "excluded_users"

        private const val EXTRA_USER_ID = "user_id"

        fun getGroupIntent(context: Context, id: String, name: String) = Intent(
            context,
            UserListActivity::class.java
        ).apply {
            putExtra(EXTRA_GROUP_ID, id)
            putExtra(EXTRA_GROUP_NAME, name)
        }

        fun getCreateGroupIntent(context: Context) = Intent(
            context,
            UserListActivity::class.java
        ).apply {
            putExtra(EXTRA_CREATE_GROUP, true)
        }

        fun getPicker(context: Context, excluded: List<String> = emptyList()) =
            Intent(context, UserListActivity::class.java).apply {
                putExtra(EXTRA_RETURN_RESULT, true)
                if (excluded.isNotEmpty()) putStringArrayListExtra(
                    EXTRA_EXCLUDED_USERS,
                    excluded as ArrayList<String>
                )
            }
    }
}