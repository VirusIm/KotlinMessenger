package com.letsbuildthatapp.kotlinmessenger.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.data.models.Conversation
import com.letsbuildthatapp.kotlinmessenger.data.models.Group
import com.letsbuildthatapp.kotlinmessenger.data.models.Message
import com.letsbuildthatapp.kotlinmessenger.data.models.User
import com.letsbuildthatapp.kotlinmessenger.utils.addBetterChildEventListener
import com.letsbuildthatapp.kotlinmessenger.utils.addSingleValueListener
import com.letsbuildthatapp.kotlinmessenger.utils.addValueListener
import com.letsbuildthatapp.kotlinmessenger.utils.databasePath
import com.letsbuildthatapp.kotlinmessenger.utils.getAttachment
import com.letsbuildthatapp.kotlinmessenger.utils.getChatsList
import com.letsbuildthatapp.kotlinmessenger.utils.getConversation
import com.letsbuildthatapp.kotlinmessenger.utils.getGroup
import com.letsbuildthatapp.kotlinmessenger.utils.getMe
import com.letsbuildthatapp.kotlinmessenger.utils.getMessage
import com.letsbuildthatapp.kotlinmessenger.utils.getUser
import com.letsbuildthatapp.kotlinmessenger.utils.getUserConversations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MessagesListViewModel : ViewModel() {

    private val _messagesList = MutableLiveData<List<Conversation>>()
    val messagesList: LiveData<List<Conversation>> = _messagesList

    private var listeners = mutableListOf<Pair<String, ValueEventListener>>()
    private var messagesListListener: ChildEventListener? = null

    init {
        FirebaseAuth.getInstance().uid?.let { uid ->
            FirebaseDatabase.getInstance()
                .getChatsList(uid)
                .addBetterChildEventListener(
                    onChildAdded = { snapshot, _ ->
                        var reference: String
                        FirebaseDatabase.getInstance()
                            .getConversation(snapshot.key!!)
                            .also { reference = it.databasePath }
                            .addSingleValueListener { conversationSnapshot ->
                                conversationSnapshot.getValue(Conversation::class.java)
                                    ?.let { chat ->
                                        _messagesList.value =
                                            (_messagesList.value ?: emptyList()).toMutableList()
                                                .apply {
                                                    removeIf { it.id == chat.id }
                                                    add(chat)
                                                    sortByDescending { it.date }
                                                }
                                    }
                            }
                            .also { listeners += reference to it }
                    },
                    onChildRemoved = { snapshot ->
                        _messagesList.value =
                            (_messagesList.value ?: emptyList()).toMutableList().apply {
                                removeIf { it.id == snapshot.key }
                            }
                    }
                )
                .also { messagesListListener = it }
        }
    }

    fun defineAuthor(
        conversation: Conversation,
        onDefined: (name: String, picture: String) -> Unit
    ) {
        when (conversation.type) {
            Conversation.TYPE_GROUP -> {
                var reference: String
                FirebaseDatabase.getInstance()
                    .getGroup(conversation.unitId)
                    .also { reference = it.databasePath }
                    .addValueListener { snapshot ->
                        snapshot.getValue(Group::class.java)?.let { group ->
                            onDefined(group.name, group.photo)
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
                            onDefined(user.username, user.profileImageUrl)
                        }
                    }
                    .also { listeners += reference to it }
            }
        }
    }

    fun defineMessage(
        conversation: Conversation,
        context: Context, // for string resource
        onDefined: (preview: String, date: String) -> Unit
    ) {
        var reference: String
        FirebaseDatabase.getInstance()
            .getMessage(
                conversation = conversation.id,
                message = conversation.lastMessageId
            )
            .also { reference = it.databasePath }
            .addSingleValueListener { snapshot ->
                snapshot.getValue(Message::class.java)?.let { message ->
                    onDefined(
                        message.text
                            ?: message.attachment?.let { context.getString(R.string.attachment) }
                            ?: "",
                        SimpleDateFormat(
                            "d MMM.",
                            Locale("uk", "UA")
                        ).format(Date(message.timestamp as Long))
                    )
                }
            }
            .also { listeners += reference to it }
    }

    fun getConversationWithUser(
        user: String,
        onFailure: (String) -> Unit,
        onGot: (Conversation) -> Unit,
    ) {
        val conversationRef = FirebaseDatabase.getInstance()
            .getUser(user)
            .getUserConversations()
            .child(FirebaseAuth.getInstance().uid!!)

        var listener: ValueEventListener? = null
        conversationRef.addSingleValueListener { snapshot ->
            if (snapshot.exists()) {
                var listener2: ValueEventListener? = null
                FirebaseDatabase.getInstance()
                    .getConversation(snapshot.value as String)
                    .addSingleValueListener { conversationSnapshot ->
                        conversationSnapshot.getValue(Conversation::class.java)?.let(onGot)

                        FirebaseDatabase.getInstance()
                            .getConversation(snapshot.value as String)
                            .removeEventListener(listener2!!)
                        conversationRef.removeEventListener(listener!!)
                    }
                    .also { listener2 = it }
            } else {
                val newId = UUID.randomUUID().toString()
                val conversation = Conversation(
                    id = newId,
                    type = Conversation.TYPE_PERSON,
                    unitId = user,
                    secondUnitId = FirebaseAuth.getInstance().uid!!
                )

                FirebaseDatabase.getInstance()
                    .getConversation(newId)
                    .setValue(conversation)
                    .addOnSuccessListener {
                        conversationRef
                            .setValue(newId)
                            .addOnSuccessListener {
                                FirebaseDatabase.getInstance()
                                    .getMe()
                                    .getUserConversations()
                                    .child(user)
                                    .setValue(newId)
                                    .addOnSuccessListener {
                                        onGot(conversation)
                                        conversationRef.removeEventListener(listener!!)
                                    }
                                    .addOnFailureListener {
                                        onFailure(it.message.toString())
                                        conversationRef.removeEventListener(listener!!)
                                    }
                            }
                            .addOnFailureListener {
                                onFailure(it.message.toString())
                                conversationRef.removeEventListener(listener!!)
                            }
                    }
                    .addOnFailureListener {
                        onFailure(it.message.toString())
                        conversationRef.removeEventListener(listener!!)
                    }
            }
        }.also { listener = it }
    }

    fun deleteConversation(conversation: Conversation) {
        when (conversation.type) {
            Conversation.TYPE_GROUP -> {
                val reference: String
                FirebaseDatabase.getInstance()
                    .getGroup(conversation.unitId)
                    .also { reference = it.databasePath }
                    .addSingleValueListener {
                        it.getValue(Group::class.java)?.let { group ->
                            group.members.keys.forEach { userId ->
                                FirebaseDatabase.getInstance()
                                    .getChatsList(userId)
                                    .child(conversation.id)
                                    .removeValue()
                            }
                        }
                        FirebaseDatabase.getInstance()
                            .getGroup(conversation.unitId)
                            .removeValue()
                    }
                    .also { listeners += reference to it }
            }

            Conversation.TYPE_PERSON -> {
                FirebaseDatabase.getInstance()
                    .getChatsList(conversation.unitId)
                    .child(conversation.id)
                    .removeValue()
                FirebaseDatabase.getInstance()
                    .getChatsList(conversation.secondUnitId!!)
                    .child(conversation.id)
                    .removeValue()

                FirebaseDatabase.getInstance()
                    .getUser(conversation.unitId)
                    .getUserConversations()
                    .child(conversation.secondUnitId)
                    .removeValue()
                FirebaseDatabase.getInstance()
                    .getUser(conversation.secondUnitId)
                    .getUserConversations()
                    .child(conversation.unitId)
                    .removeValue()
            }
        }
        FirebaseDatabase.getInstance()
            .getConversation(conversation.id)
            .removeValue()
        FirebaseStorage.getInstance()
            .getAttachment(conversation.id)
            .listAll()
            .addOnSuccessListener { ref ->
                ref.items.forEach { it.delete() }
            }
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach {
            FirebaseDatabase.getInstance()
                .getReference(it.first)
                .removeEventListener(it.second)
        }
        messagesListListener?.let {
            FirebaseDatabase.getInstance()
                .getChatsList(FirebaseAuth.getInstance().uid ?: "")
                .removeEventListener(it)
        }
    }
}