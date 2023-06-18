package com.letsbuildthatapp.kotlinmessenger.viewmodel

import android.content.Context
import android.net.Uri
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
import com.letsbuildthatapp.kotlinmessenger.R
import com.letsbuildthatapp.kotlinmessenger.data.models.Conversation
import com.letsbuildthatapp.kotlinmessenger.data.models.Group
import com.letsbuildthatapp.kotlinmessenger.data.models.Message
import com.letsbuildthatapp.kotlinmessenger.data.models.User
import com.letsbuildthatapp.kotlinmessenger.utils.addBetterChildEventListener
import com.letsbuildthatapp.kotlinmessenger.utils.addSingleValueListener
import com.letsbuildthatapp.kotlinmessenger.utils.databasePath
import com.letsbuildthatapp.kotlinmessenger.utils.getChatsList
import com.letsbuildthatapp.kotlinmessenger.utils.getConversation
import com.letsbuildthatapp.kotlinmessenger.utils.getGroup
import com.letsbuildthatapp.kotlinmessenger.utils.getMessage
import com.letsbuildthatapp.kotlinmessenger.utils.getProfilePicture
import com.letsbuildthatapp.kotlinmessenger.utils.getUser
import com.letsbuildthatapp.kotlinmessenger.utils.getUsers
import java.util.UUID

class UserListViewModel(
    private val groupId: String?,
    private val excludedUsers: List<String>
) : ViewModel() {

    private val fullList = mutableListOf<User>()
    private var searchRequest = ""

    private val _users = MutableLiveData<List<User>>()
    val users: LiveData<List<User>> = _users

    private val _groupPhoto = MutableLiveData<String>()
    val groupPhoto: LiveData<String> = _groupPhoto

    private var groupListener: ChildEventListener? = null
    private val listeners = mutableListOf<Pair<String, ValueEventListener>>()

    init {
        if (groupId != null) {
            var reference: String
            FirebaseDatabase.getInstance()
                .getGroup(groupId)
                .child("members")
                .addBetterChildEventListener(
                    onChildAdded = { snapshot, _ ->
                        FirebaseDatabase.getInstance()
                            .getUser(snapshot.key!!)
                            .also { reference = it.databasePath }
                            .addSingleValueListener { userSnapshot ->
                                userSnapshot.getValue(User::class.java)?.let { user ->
                                    _users.value = fullList.apply {
                                        removeIf { user.uid == it.uid }
                                        add(user)
                                        sortBy { it.username }
                                    }.filter {
                                        it.uid !in excludedUsers &&
                                                (searchRequest.isBlank()
                                                        || it.username.startsWith(searchRequest.trim()))
                                    }
                                }
                            }
                            .also { listeners += reference to it }
                    },
                    onChildRemoved = { snapshot ->
                        _users.value = fullList.apply {
                            removeIf { snapshot.key == it.uid }
                        }.filter {
                            it.uid !in excludedUsers &&
                                    (searchRequest.isBlank() || it.username.startsWith(searchRequest.trim()))
                        }
                    }
                )
                .also { groupListener = it }

            val ref: String
            lateinit var listener: ValueEventListener
            FirebaseDatabase.getInstance()
                .getGroup(groupId)
                .also { ref = it.databasePath }
                .addSingleValueListener {
                    FirebaseDatabase.getInstance()
                        .getReference(ref)
                        .removeEventListener(listener)

                    it.getValue(Group::class.java)?.let { group ->
                        _groupPhoto.value = group.photo
                    }
                }
                .also { listener = it }
        } else {
            val reference: String
            FirebaseDatabase.getInstance()
                .getUsers()
                .also { reference = it.databasePath }
                .addSingleValueListener { snapshot ->
                    snapshot.children.forEach { userSnapshot ->
                        userSnapshot.getValue(User::class.java)?.let { user ->
                            _users.value = fullList.apply {
                                removeIf { user.uid == it.uid }
                                add(user)
                                sortBy { it.username }
                            }.filter {
                                it.uid !in excludedUsers &&
                                        (searchRequest.isBlank() || it.username.startsWith(
                                            searchRequest.trim()
                                        ))
                            }
                        }
                    }
                }
                .also { listeners += reference to it }
        }
    }

    fun search(query: String) {
        searchRequest = query
        _users.value = fullList.filter {
            it.uid !in excludedUsers &&
                    searchRequest.isBlank() || it.username.startsWith(searchRequest.trim())
        }
    }

    fun createGroup(
        name: String,
        photo: Uri?,
        members: List<String>,
        onCreatedListener: (conversation: Conversation) -> Unit,
    ) {
        if (name.isBlank() || members.isEmpty()) return

        fun createGroup(photoUri: String) {
            val groupId = UUID.randomUUID().toString()
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                unitId = groupId,
                type = Conversation.TYPE_GROUP,
            )

            FirebaseDatabase.getInstance()
                .getConversation(conversation.id)
                .setValue(conversation)
                .addOnSuccessListener {
                    FirebaseDatabase.getInstance()
                        .getGroup(groupId)
                        .setValue(
                            Group(
                                id = groupId,
                                name = name,
                                photo = photoUri,
                                members = (members.toMutableList().apply {
                                    add(FirebaseAuth.getInstance().uid!!)
                                }).associateWith { it },
                                conversation = conversation.id
                            )
                        )
                        .addOnSuccessListener {
                            onCreatedListener(conversation)
                        }
                }
        }

        if (photo != null) {
            val ref = FirebaseStorage.getInstance()
                .getProfilePicture(UUID.randomUUID().toString())
            ref.putFile(photo).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener {
                    createGroup(it.toString())
                }
            }
        } else {
            createGroup("")
        }
    }

    fun deleteUsers(
        users: List<User>,
        context: Context // for string resources
    ) {
        val ref = FirebaseDatabase.getInstance().getGroup(groupId!!)
        ref.addSingleValueListener { snapshot ->
            snapshot.getValue(Group::class.java)?.let { group ->
                val newMembers = group.members.filter { filter ->
                    !users.any { it.uid == filter.key }
                }
                users.forEach { user ->
                    ref.child("members").child(user.uid).removeValue()
                    FirebaseDatabase.getInstance()
                        .getChatsList(user.uid)
                        .child(group.conversation)
                        .removeValue()

                    val newId = UUID.randomUUID().toString()
                    FirebaseDatabase.getInstance()
                        .getMessage(
                            conversation = group.conversation,
                            message = newId
                        )
                        .setValue(
                            Message(
                                id = newId,
                                text = context.getString(R.string.user_removed, user.username),
                                system = true,
                                timestamp = ServerValue.TIMESTAMP
                            )
                        )
                        .addOnSuccessListener {
                            FirebaseDatabase.getInstance()
                                .getConversation(group.conversation)
                                .let {
                                    it.child("lastMessageId").setValue(newId)
                                    it.child("date").setValue(ServerValue.TIMESTAMP)
                                }
                            newMembers.forEach { member ->
                                FirebaseDatabase.getInstance()
                                    .getChatsList(member.key)
                                    .child(group.conversation)
                                    .setValue(ServerValue.TIMESTAMP)
                            }
                        }
                }
            }
        }
    }

    fun addUser(
        userId: String, context: Context // for string resources
    ) {
        var reference: String
        FirebaseDatabase.getInstance()
            .getUser(userId)
            .also { reference = it.databasePath }
            .addSingleValueListener { userSnapshot ->
                userSnapshot.getValue(User::class.java)?.let { user ->
                    FirebaseDatabase.getInstance()
                        .getGroup(groupId!!)
                        .also { reference = it.databasePath }
                        .addSingleValueListener { snapshot ->
                            snapshot.getValue(Group::class.java)?.let { group ->
                                FirebaseDatabase.getInstance()
                                    .getGroup(groupId)
                                    .child("members")
                                    .child(user.uid)
                                    .setValue(user.uid)
                                    .addOnSuccessListener {
                                        val newId = UUID.randomUUID().toString()

                                        FirebaseDatabase.getInstance()
                                            .getMessage(
                                                conversation = group.conversation,
                                                message = newId
                                            )
                                            .setValue(
                                                Message(
                                                    id = newId,
                                                    text = context.getString(
                                                        R.string.user_added,
                                                        user.username
                                                    ),
                                                    system = true,
                                                    timestamp = ServerValue.TIMESTAMP
                                                )
                                            )
                                            .addOnSuccessListener {
                                                FirebaseDatabase.getInstance()
                                                    .getConversation(group.conversation)
                                                    .let {
                                                        it.child("lastMessageId").setValue(newId)
                                                        it.child("date")
                                                            .setValue(ServerValue.TIMESTAMP)
                                                    }

                                                (group.members).forEach { member ->
                                                    FirebaseDatabase.getInstance()
                                                        .getChatsList(member.key)
                                                        .child(group.conversation)
                                                        .setValue(ServerValue.TIMESTAMP)
                                                }
                                                FirebaseDatabase.getInstance()
                                                    .getChatsList(user.uid)
                                                    .child(group.conversation)
                                                    .setValue(ServerValue.TIMESTAMP)
                                            }
                                    }
                            }
                        }.also { listeners += reference to it }
                }
            }.also { listeners += reference to it }
    }

    fun updateGroupPhoto(uri: Uri) {
        var ref: String
        FirebaseDatabase.getInstance()
            .getGroup(groupId!!)
            .also { ref = it.databasePath }
            .addSingleValueListener { groupSnapshot ->
                groupSnapshot.getValue(Group::class.java)?.let { group ->
                    if (group.photo.isNotEmpty()) {
                        FirebaseStorage.getInstance()
                            .getReferenceFromUrl(group.photo)
                            .delete()
                    }
                    val pictureRef = FirebaseStorage.getInstance()
                        .getProfilePicture(UUID.randomUUID().toString())

                    pictureRef
                        .putFile(uri)
                        .addOnSuccessListener {
                            pictureRef.downloadUrl.addOnSuccessListener {
                                FirebaseDatabase.getInstance()
                                    .getGroup(groupId)
                                    .child("photo")
                                    .setValue(it.toString())
                                _groupPhoto.value = it.toString()
                            }
                        }
                }
            }
            .also { listeners += ref to it }
    }

    fun updateGroupName(newName: String) {
        FirebaseDatabase.getInstance()
            .getGroup(groupId!!)
            .child("name")
            .setValue(newName)
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach {
            FirebaseDatabase.getInstance()
                .getReference(it.first)
                .removeEventListener(it.second)
        }
        groupListener?.let {
            FirebaseDatabase.getInstance()
                .getGroup(groupId!!)
                .removeEventListener(it)
        }
    }

    class Factory(
        private val groupId: String?,
        private val excludedUsers: ArrayList<String>?
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return UserListViewModel(
                groupId = groupId,
                excludedUsers = (excludedUsers?.toMutableList() ?: mutableListOf()).apply {
                    add(FirebaseAuth.getInstance().uid!!)
                }
            ) as T
        }
    }
}