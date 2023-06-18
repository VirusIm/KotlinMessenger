package com.letsbuildthatapp.kotlinmessenger.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.data.models.Conversation
import com.letsbuildthatapp.kotlinmessenger.data.models.Group
import com.letsbuildthatapp.kotlinmessenger.data.models.Message
import com.letsbuildthatapp.kotlinmessenger.data.models.User
import com.letsbuildthatapp.kotlinmessenger.utils.Const
import com.letsbuildthatapp.kotlinmessenger.utils.addBetterChildEventListener
import com.letsbuildthatapp.kotlinmessenger.utils.addSingleValueListener
import com.letsbuildthatapp.kotlinmessenger.utils.addValueListener
import com.letsbuildthatapp.kotlinmessenger.utils.databasePath
import com.letsbuildthatapp.kotlinmessenger.utils.getAttachment
import com.letsbuildthatapp.kotlinmessenger.utils.getChatsList
import com.letsbuildthatapp.kotlinmessenger.utils.getConversation
import com.letsbuildthatapp.kotlinmessenger.utils.getGroup
import com.letsbuildthatapp.kotlinmessenger.utils.getMessage
import com.letsbuildthatapp.kotlinmessenger.utils.getMessages
import com.letsbuildthatapp.kotlinmessenger.utils.getUser
import java.util.UUID

class ChatViewModel private constructor(private val conversation: Conversation) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessageItem>>()
    val messages: LiveData<List<ChatMessageItem>> = _messages

    private val listeners = mutableListOf<Pair<String, ValueEventListener>>()
    private var messagesListener: ChildEventListener? = null

    private val uploadingTasks = mutableMapOf<String, UploadTask>()
    private val tasksToBeCancelled = mutableListOf<String>()

    init {
        FirebaseDatabase.getInstance()
            .getMessages(conversation.id)
            .addBetterChildEventListener(
                onChildAdded = { snapshot, _ ->
                    snapshot.getValue(Message::class.java)?.let { message ->
                        _messages.value =
                            (_messages.value ?: emptyList()).toMutableList().apply {
                                removeIf { it.message.id == message.id }
                                add(
                                    when {
                                        message.fromId == FirebaseAuth.getInstance().uid ->
                                            ChatMessageItem.Sent(message)

                                        message.system -> ChatMessageItem.System(message)

                                        else -> ChatMessageItem.Received(message)
                                    }
                                )
                                sortByDescending { it.message.timestamp as Long }
                            }
                    }
                }
            )
            .also { messagesListener = it }
    }

    fun parseConversation(onDefinedTitle: (String) -> Unit) {
        when (conversation.type) {
            Conversation.TYPE_GROUP -> {
                var reference: String
                FirebaseDatabase.getInstance()
                    .getGroup(conversation.unitId)
                    .also { reference = it.databasePath }
                    .addValueListener { snapshot ->
                        snapshot.getValue(Group::class.java)?.let { group ->
                            onDefinedTitle(group.name)
                        }
                    }
                    .also { listeners += reference to it }
            }

            Conversation.TYPE_PERSON -> {
                var reference: String
                FirebaseDatabase.getInstance()
                    .getUser(
                        conversation.unitId
                            .takeIf { it != FirebaseAuth.getInstance().uid!! }
                            ?: conversation.secondUnitId!!
                    )
                    .also { reference = it.databasePath }
                    .addSingleValueListener { snapshot ->
                        snapshot.getValue(User::class.java)?.let { user ->
                            onDefinedTitle(user.username)
                        }
                    }
                    .also { listeners += reference to it }
            }
        }
    }

    fun defineUser(
        id: String,
        onDefined: (username: String, profilePicture: String) -> Unit
    ) {
        var reference: String
        FirebaseDatabase.getInstance()
            .getUser(id)
            .also { reference = it.databasePath }
            .addSingleValueListener { snapshot ->
                snapshot.getValue(User::class.java)?.let { user ->
                    onDefined(user.username, user.profileImageUrl)
                }
            }
            .also { listeners += reference to it }
    }

    fun sendMessage(text: String) {
        val messageId = UUID.randomUUID().toString()

        FirebaseDatabase.getInstance()
            .getMessage(
                conversation = conversation.id,
                message = messageId
            )
            .setValue(
                Message(
                    id = messageId,
                    text = text.trim(),
                    fromId = FirebaseAuth.getInstance().uid!!,
                    timestamp = ServerValue.TIMESTAMP
                )
            )
            .addOnSuccessListener {
                updateLastMessageId(messageId)
            }
    }

    private fun updateLastMessageId(messageId: String) {
        FirebaseDatabase.getInstance()
            .getConversation(conversation.id)
            .let {
                it.child("lastMessageId").setValue(messageId)
                it.child("date").setValue(ServerValue.TIMESTAMP)
            }

        when (conversation.type) {
            Conversation.TYPE_PERSON -> {
                listOf(
                    conversation.unitId,
                    conversation.secondUnitId
                ).forEach { userId ->
                    FirebaseDatabase.getInstance()
                        .getChatsList(userId!!)
                        .child(conversation.id)
                        .setValue(ServerValue.TIMESTAMP)
                }
            }

            Conversation.TYPE_GROUP -> {
                var reference: String
                FirebaseDatabase.getInstance()
                    .getGroup(conversation.unitId)
                    .also { reference = it.databasePath }
                    .addSingleValueListener { snapshot ->
                        snapshot.getValue(Group::class.java)?.let { group ->
                            group.members.forEach { user ->
                                FirebaseDatabase.getInstance()
                                    .getChatsList(user.key)
                                    .child(conversation.id)
                                    .setValue(ServerValue.TIMESTAMP)
                            }
                        }
                    }
                    .also { listeners += reference to it }
            }
        }
    }

    fun editMessage(message: Message, newText: String) {
        FirebaseDatabase.getInstance()
            .getMessage(
                conversation = conversation.id,
                message = message.id
            )
            .setValue(
                message.copy(
                    text = newText,
                    edited = true
                )
            )
    }

    fun sendAttachment(
        uri: Uri,
        context: Context
    ) {
        val cursor = context.contentResolver
            .query(uri, null, null, null, null) ?: return
        val size = cursor
            .apply { moveToFirst() }
            .getInt(cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 } ?: return)
        cursor.close()

        if (size > Const.MAX_ATTACHMENT_SIZE) {
            Toast
                .makeText(context, context.getString(R.string.file_is_too_big), Toast.LENGTH_SHORT)
                .show()
            return
        }

        val newId = "${conversation.id}/${UUID.randomUUID()}"
        val ref = FirebaseStorage.getInstance().getAttachment(newId)

        _messages.value = (_messages.value ?: emptyList()).toMutableList().apply {
            add(
                ChatMessageItem.Sent(
                    message = Message(
                        id = UUID.randomUUID().toString(),
                        fromId = FirebaseAuth.getInstance().uid!!,
                        timestamp = Long.MAX_VALUE
                    ),
                    uploadingAttachment = uri,
                    cancelUuid = newId
                )
            )
            sortByDescending { it.message.timestamp as Long }
        }

        ref.putFile(uri)
            .also { uploadTask -> uploadingTasks[newId] = uploadTask }
            .addOnSuccessListener {
                _messages.value = (_messages.value ?: emptyList()).toMutableList().apply {
                    removeIf {
                        it is ChatMessageItem.Sent && it.uploadingAttachment == uri
                    }
                }
                uploadingTasks.remove(newId)

                if (tasksToBeCancelled.contains(newId)) {
                    ref.delete()
                    tasksToBeCancelled.remove(newId)
                    return@addOnSuccessListener
                }

                ref.downloadUrl.addOnSuccessListener { attachmentUri ->
                    val newMsgId = UUID.randomUUID().toString()
                    FirebaseDatabase.getInstance()
                        .getMessage(
                            conversation = conversation.id,
                            message = newMsgId
                        )
                        .setValue(
                            Message(
                                id = newMsgId,
                                fromId = FirebaseAuth.getInstance().uid!!,
                                timestamp = ServerValue.TIMESTAMP,
                                attachment = attachmentUri.toString()
                            )
                        )
                        .addOnSuccessListener {
                            updateLastMessageId(newMsgId)
                        }
                }
            }
            .addOnCanceledListener {
                _messages.value = (_messages.value ?: emptyList()).toMutableList().apply {
                    removeIf {
                        it is ChatMessageItem.Sent && it.uploadingAttachment == uri
                    }
                }
                uploadingTasks.remove(newId)
            }
    }

    fun cancel(id: String) {
        uploadingTasks[id]?.let { task ->
            task.cancel()
            _messages.value = (_messages.value ?: emptyList()).toMutableList().apply {
                removeIf {
                    it is ChatMessageItem.Sent && it.cancelUuid == id
                }
            }
            uploadingTasks.remove(id)
            tasksToBeCancelled += id
        }
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach {
            FirebaseDatabase.getInstance()
                .getReference(it.first)
                .removeEventListener(it.second)
        }
        messagesListener?.let {
            FirebaseDatabase.getInstance()
                .getMessages(conversation.id)
                .removeEventListener(it)
        }
    }

    sealed class ChatMessageItem(val message: Message) {

        class Sent(
            message: Message,
            val uploadingAttachment: Uri? = null,
            val cancelUuid: String? = null
        ) : ChatMessageItem(message)

        class Received(message: Message) : ChatMessageItem(message)

        class System(message: Message) : ChatMessageItem(message)
    }

    class Factory(
        private val conversation: Conversation
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(conversation) as T
        }
    }
}