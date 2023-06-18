package com.letsbuildthatapp.kotlinmessenger.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.elveum.elementadapter.adapter
import com.elveum.elementadapter.addBinding
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.data.models.Conversation
import com.letsbuildthatapp.kotlinmessenger.databinding.ActivityChatBinding
import com.letsbuildthatapp.kotlinmessenger.databinding.MessageReceivedItemBinding
import com.letsbuildthatapp.kotlinmessenger.databinding.MessageSentItemBinding
import com.letsbuildthatapp.kotlinmessenger.databinding.MessageSystemItemBinding
import com.letsbuildthatapp.kotlinmessenger.utils.Const
import com.letsbuildthatapp.kotlinmessenger.viewmodel.ChatViewModel
import com.letsbuildthatapp.kotlinmessenger.viewmodel.ChatViewModel.ChatMessageItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream

class ChatActivity : AppCompatActivity(R.layout.activity_chat) {

    private val extraConversation by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.extras!!.getParcelable(EXTRA_CONVERSATION, Conversation::class.java)!!
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_CONVERSATION)!!
        }
    }

    private val binding: ActivityChatBinding by viewBinding()
    private val viewModel: ChatViewModel by viewModels { ChatViewModel.Factory(extraConversation) }

    private var selectedItem: ChatMessageItem.Sent? = null

    private val adapter = adapter {
        addBinding<ChatMessageItem.Received, MessageReceivedItemBinding> {
            bind { item ->
                picture.shapeAppearanceModel = picture.shapeAppearanceModel
                    .withCornerSize { rect -> rect.height() / 2 }
                message.apply {
                    isVisible = item.message.text != null
                    text = item.message.text
                }
                attachment.apply {
                    isVisible = item.message.attachment != null
                    shapeAppearanceModel = shapeAppearanceModel
                        .withCornerSize(10 * resources.displayMetrics.density)
                    load(item.message.attachment)
                }
                edited.visibility = if (item.message.edited) View.VISIBLE else View.GONE

                viewModel.defineUser(item.message.fromId) { _, profileImageUrl ->
                    picture.load(profileImageUrl)
                }
            }
            listeners {
                picture.onClick {
                    startActivity(
                        ProfileActivity.getProfileIntent(this@ChatActivity, it.message.fromId)
                    )
                }
                attachment.onClick {
                    it.message.attachment?.let(::openPhotoInExternalApp)
                }
            }
            areItemsSame = { old, new -> old.message.id == new.message.id }
            areContentsSame = { old, new ->
                old.message.text == new.message.text && old.message.attachment == new.message.attachment && old.message.edited == new.message.edited
            }
        }
        addBinding<ChatMessageItem.Sent, MessageSentItemBinding> {
            bind { item ->
                picture.shapeAppearanceModel = picture.shapeAppearanceModel
                    .withCornerSize { rect -> rect.height() / 2 }
                message.apply {
                    isVisible = item.message.text != null
                    text = item.message.text
                }

                attachment.isVisible = item.message.attachment != null
                btnCancel.isVisible = item.uploadingAttachment != null
                if (item.message.attachment != null || item.uploadingAttachment != null) {
                    attachment.apply {
                        shapeAppearanceModel = shapeAppearanceModel
                            .withCornerSize(10 * resources.displayMetrics.density)
                        load(item.message.attachment ?: item.uploadingAttachment)
                    }
                }

                edited.isVisible = item.message.edited

                viewModel.defineUser(item.message.fromId) { _, profileImageUrl ->
                    picture.load(profileImageUrl)
                }

                root.isSelected = selectedItem == item
            }
            listeners {
                picture.onClick {
                    startActivity(
                        ProfileActivity.getProfileIntent(this@ChatActivity, it.message.fromId)
                    )
                }
                attachment.onClick {
                    it.message.attachment?.let(::openPhotoInExternalApp)
                }
                btnCancel.onClick {
                    it.cancelUuid?.let(viewModel::cancel)
                }

                messageBubble.onClick {
                    when {
                        it.message.id == selectedItem?.message?.id -> {
                            selectedItem = null
                            root.isSelected = false
                            changeToolbarToNormalState()
                        }

                        selectedItem != null && it.message.text != null -> {
                            val previousIndex =
                                viewModel.messages.value?.indexOf(selectedItem!!)
                            selectedItem = it
                            root.isSelected = true
                            notifyItemChanged(previousIndex ?: 0)
                        }
                    }
                }
                messageBubble.onLongClick {
                    when {
                        it.message.id == selectedItem?.message?.id -> {
                            selectedItem = null
                            root.isSelected = false
                            changeToolbarToNormalState()
                            true
                        }

                        selectedItem != null && it.message.text != null -> {
                            val previousIndex =
                                viewModel.messages.value?.indexOf(selectedItem!!)
                            selectedItem = it
                            root.isSelected = true
                            notifyItemChanged(previousIndex ?: 0)
                            true
                        }

                        it.message.text != null -> {
                            selectedItem = it
                            root.isSelected = true
                            changeToolbarToEditState()
                            true
                        }

                        else -> false
                    }
                }
            }
            areItemsSame = { old, new -> old.message.id == new.message.id }
            areContentsSame = { old, new ->
                old.message.text == new.message.text && old.message.attachment == new.message.attachment && old.message.edited == new.message.edited
            }
        }
        addBinding<ChatMessageItem.System, MessageSystemItemBinding> {
            bind {
                root.text = it.message.text

                if (viewModel.messages.value
                        ?.getOrNull(viewModel.messages.value!!.indexOf(it) - 1)
                            !is ChatMessageItem.System
                ) root.updatePadding(bottom = root.paddingTop)
                else root.updatePadding(bottom = 0)
            }
            areItemsSame = { old, new -> old.message.id == new.message.id }
            areContentsSame = { _, _ -> false } // for dealing with paddings
        }
    }

    private fun openPhotoInExternalApp(uri: String) {
        val loader = ImageLoader.Builder(this)
            .crossfade(true)
            .build()
        val request = ImageRequest.Builder(this)
            .data(uri)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            val drawable = (loader.execute(request) as? SuccessResult)?.drawable ?: return@launch

            val file = createTempFile("temp_image", ".jpg")
            val saved = drawable.toBitmap()
                .compress(Bitmap.CompressFormat.JPEG, 100, FileOutputStream(file))

            if (saved) try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        FileProvider.getUriForFile(
                            this@ChatActivity,
                            "com.letsbuildthatapp.kotlinmessenger.fileprovider",
                            file
                        ),
                        "image/jpeg"
                    )
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
            }

        }
    }

    private var lastLength = -1

    private val pickFileContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) viewModel.sendAttachment(uri, this)

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        with(binding) {
            if (extraConversation.type == Conversation.TYPE_GROUP) {
                btnGroup.apply {
                    isVisible = true
                    setOnClickListener {
                        startActivity(
                            UserListActivity.getGroupIntent(
                                this@ChatActivity,
                                extraConversation.unitId,
                                toolbarTitle.text.toString()
                            )
                        )
                    }
                }
            }

            rvMessagesList.layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                reverseLayout = true
            }
            rvMessagesList.adapter = adapter

            btnSend.setOnClickListener {
                val msg = textFieldMessage.text.toString()

                if (msg.isNotBlank()) {
                    if (selectedItem != null) {
                        val index = viewModel.messages.value?.indexOf(selectedItem!!)
                        viewModel.editMessage(selectedItem!!.message, msg)
                        selectedItem = null
                        notifyItemChanged(index ?: 0)
                        changeToolbarToNormalState()
                    } else {
                        viewModel.sendMessage(msg)
                    }
                    textFieldMessage.text = null
                }
            }
            btnAttach.setOnClickListener {
                pickFileContract.launch(Const.ATTACHMENTS_MIME_TYPE)
            }
            btnBack.setOnClickListener {
                if (selectedItem == null) {
                    finish()
                } else {
                    val index = viewModel.messages.value?.indexOf(selectedItem!!)
                    selectedItem = null
                    notifyItemChanged(index ?: 0)
                    changeToolbarToNormalState()
                }
            }
            btnEdit.setOnClickListener {
                textFieldMessage.apply {
                    setText(selectedItem!!.message.text)
                    requestFocus()

                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        viewModel.messages.observe(this) {
            adapter.submitList(it)
            if (lastLength < it.size) {
                Handler(Looper.getMainLooper()).postDelayed(100) {
                    binding.rvMessagesList.smoothScrollToPosition(0)
                }
            }
            lastLength = it.size
        }
        viewModel.parseConversation(
            onDefinedTitle = {
                binding.toolbarTitle.text = it
            }
        )
    }

    private fun changeToolbarToNormalState() = with(binding) {
        btnEdit.isVisible = false
        btnGroup.isVisible = extraConversation.type == Conversation.TYPE_GROUP
    }

    private fun changeToolbarToEditState() = with(binding) {
        btnEdit.isVisible = true
        btnGroup.isVisible = false
    }

    private fun notifyItemChanged(index: Int) {
        adapter.notifyItemChanged(index)
    }

    companion object {

        private const val EXTRA_CONVERSATION = "conversation"

        fun getIntent(
            context: Context,
            conversation: Conversation,
        ) = Intent(context, ChatActivity::class.java).apply {
            putExtra(EXTRA_CONVERSATION, conversation)
        }
    }
}