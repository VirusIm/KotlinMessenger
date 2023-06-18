package com.letsbuildthatapp.kotlinmessenger.ui

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import coil.load
import com.elveum.elementadapter.context
import com.elveum.elementadapter.simpleAdapter
import com.google.firebase.auth.FirebaseAuth
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.data.models.Conversation
import com.letsbuildthatapp.kotlinmessenger.databinding.ActivityMessagesListBinding
import com.letsbuildthatapp.kotlinmessenger.databinding.ChatListItemBinding
import com.letsbuildthatapp.kotlinmessenger.viewmodel.MessagesListViewModel

class MessagesListActivity : AppCompatActivity(R.layout.activity_messages_list) {

    private val binding: ActivityMessagesListBinding by viewBinding()
    private val viewModel: MessagesListViewModel by viewModels()

    private var selectedItem: Conversation? = null

    private val adapter = simpleAdapter<Conversation, ChatListItemBinding> {
        bind {
            picture.shapeAppearanceModel = picture.shapeAppearanceModel
                .withCornerSize { rect -> rect.height() / 2 }

            viewModel.defineAuthor(it) { dataName, dataPicture ->
                name.text = dataName
                picture.load(dataPicture) {
                    val placeholder = ColorDrawable(ContextCompat.getColor(context(), R.color.placeholder))
                    placeholder(placeholder)
                    error(placeholder)
                    crossfade(true)
                }
            }
            viewModel.defineMessage(it, context()) { dataPreview, dataDate ->
                preview.text = dataPreview
                date.text = dataDate
            }

            root.isSelected = selectedItem == it
        }
        listeners {
            root.onClick {
                when {
                    it.id == selectedItem?.id -> {
                        selectedItem = null
                        root.isSelected = false
                        changeToolbarToNormalState()
                    }

                    selectedItem != null -> {
                        val previousIndex = viewModel.messagesList.value?.indexOf(selectedItem!!)
                        selectedItem = it
                        root.isSelected = true
                        notifyItemChanged(previousIndex ?: 0)
                    }

                    else -> {
                        startActivity(
                            ChatActivity.getIntent(
                                context = this@MessagesListActivity,
                                conversation = it
                            )
                        )
                    }
                }
            }
            root.onLongClick {
                when {
                    it.id == selectedItem?.id -> {
                        selectedItem = null
                        root.isSelected = false
                        changeToolbarToNormalState()
                    }

                    selectedItem != null -> {
                        val previousIndex = viewModel.messagesList.value?.indexOf(selectedItem!!)
                        selectedItem = it
                        root.isSelected = true
                        notifyItemChanged(previousIndex ?: 0)
                    }

                    else -> {
                        selectedItem = it
                        root.isSelected = true
                        changeToolbarToDeletionState()
                    }
                }
                true
            }
        }
        areItemsSame = { old, new -> old.id == new.id }
        areContentsSame = { old, new -> old == new && new.id != selectedItem?.id }
    }

    private val pickUserContract = registerForActivityResult(UserListActivity.PickUser()) { user ->
        if (user != null) {
            viewModel.getConversationWithUser(
                user = user,
                onFailure = {
                    Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                }
            ) {
                startActivity(ChatActivity.getIntent(this, it))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        checkIfUserIsSignedIn()

        with(binding) {
            rvChatsList.adapter = adapter

            btnProfile.setOnClickListener {
                startActivity(ProfileActivity.getEditingIntent(this@MessagesListActivity))
            }
            btnNewMessage.setOnClickListener {
                pickUserContract.launch(emptyList())
            }
            btnExit.setOnClickListener {
                FirebaseAuth.getInstance().signOut()
                startActivity(
                    Intent(this@MessagesListActivity, SignUpActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
            btnNewGroup.setOnClickListener {
                startActivity(UserListActivity.getCreateGroupIntent(this@MessagesListActivity))
            }

            btnBack.setOnClickListener {
                val index = viewModel.messagesList.value?.indexOf(selectedItem!!)
                selectedItem = null
                notifyItemChanged(index ?: 0)
                changeToolbarToNormalState()
            }
            btnDelete.setOnClickListener {
                viewModel.deleteConversation(selectedItem!!)
                changeToolbarToNormalState()
            }
        }
        viewModel.messagesList.observe(this) {
            adapter.submitList(it)
        }
    }

    private fun changeToolbarToDeletionState() = with(binding) {
        btnBack.isVisible = true
        btnDelete.isVisible = true

        btnProfile.isVisible = false
        btnNewMessage.isVisible = false
        btnExit.isVisible = false
        btnNewGroup.isVisible = false
    }

    private fun changeToolbarToNormalState() = with(binding) {
        btnBack.isVisible = false
        btnDelete.isVisible = false

        btnProfile.isVisible = true
        btnNewMessage.isVisible = true
        btnExit.isVisible = true
        btnNewGroup.isVisible = true
    }

    private fun notifyItemChanged(index: Int) {
        adapter.notifyItemChanged(index)
    }

    private fun checkIfUserIsSignedIn() {
        if (FirebaseAuth.getInstance().uid == null) {
            startActivity(
                Intent(this, SignUpActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }
}
