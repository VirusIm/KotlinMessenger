package com.letsbuildthatapp.kotlinmessenger.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

fun DatabaseReference.addBetterChildEventListener(
    onChildAdded: (DataSnapshot, String?) -> Unit = { _, _ -> },
    onChildChanged: (DataSnapshot, String?) -> Unit = onChildAdded,
    onChildMoved: (DataSnapshot, String?) -> Unit = { _, _ -> },
    onChildRemoved: (DataSnapshot) -> Unit = {},
    onCancelled: (DatabaseError) -> Unit = {}
) = object : ChildEventListener {

    override fun onChildAdded(snapshot: DataSnapshot, p1: String?) =
        onChildAdded(snapshot, p1)

    override fun onChildChanged(snapshot: DataSnapshot, p1: String?) =
        onChildChanged(snapshot, p1)

    override fun onChildMoved(snapshot: DataSnapshot, p1: String?) =
        onChildMoved(snapshot, p1)

    override fun onChildRemoved(snapshot: DataSnapshot) =
        onChildRemoved(snapshot)

    override fun onCancelled(error: DatabaseError) =
        onCancelled(error)
}.also { addChildEventListener(it) }

fun DatabaseReference.addSingleValueListener(
    onCancelled: (DatabaseError) -> Unit = {},
    onDataChange: (DataSnapshot) -> Unit = {},
) = object : ValueEventListener {
    override fun onCancelled(error: DatabaseError) = onCancelled(error)
    override fun onDataChange(snapshot: DataSnapshot) = onDataChange(snapshot)
}.also { addListenerForSingleValueEvent(it) }

fun DatabaseReference.addValueListener(
    onCancelled: (DatabaseError) -> Unit = {},
    onDataChange: (DataSnapshot) -> Unit = {},
) = object : ValueEventListener {
    override fun onCancelled(error: DatabaseError) = onCancelled(error)
    override fun onDataChange(snapshot: DataSnapshot) = onDataChange(snapshot)
}.also { addValueEventListener(it) }

fun FirebaseDatabase.getMe() = getUser(FirebaseAuth.getInstance().uid!!)

fun FirebaseDatabase.getUser(id: String) = reference
    .child("users")
    .child(id)

fun FirebaseDatabase.getUsers() = reference
    .child("users")

fun FirebaseDatabase.getGroup(id: String) = reference
    .child("groups")
    .child(id)

fun FirebaseDatabase.getChatsList(userId: String) = reference
    .child("chats-lists")
    .child(userId)

fun FirebaseDatabase.getMessage(conversation: String, message: String) = reference
    .child("conversations")
    .child(conversation)
    .child("messages")
    .child(message)

fun FirebaseDatabase.getConversation(conversation: String) = reference
    .child("conversations")
    .child(conversation)

fun FirebaseDatabase.getMessages(conversation: String) = reference
    .child("conversations")
    .child(conversation)
    .child("messages")

fun DatabaseReference.getUserConversations() = child("conversations")

fun FirebaseStorage.getProfilePicture(uid: String) = reference
    .child("profile-images")
    .child(uid)

fun FirebaseStorage.getAttachment(uid: String) = reference
    .child("attachments")
    .child(uid)

val DatabaseReference.databasePath: String
    get() = toString().substring(FirebaseDatabase.getInstance().reference.toString().length - 1)