package com.gappein.sdk.data.db

import com.gappein.sdk.client.ChatClient
import com.gappein.sdk.model.ChannelUsers
import com.gappein.sdk.model.Message
import com.gappein.sdk.model.User
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot


class FirebaseDbManagerImpl : FirebaseDbManager {

    private val database: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val channelReference = database.collection(CHANNEL_COLLECTION)
    private val userReference = database.collection(USER_COLLECTION)

    companion object {
        private const val USER_COLLECTION = "users"
        private const val MESSAGES_COLLECTION = "messages"
        private const val CHANNEL_COLLECTION = "channel"
        private const val CHANNEL_ID = "channelId"
    }

    override fun createUser(user: User, onSuccess: (User) -> Unit, onError: (Exception) -> Unit) {

        val reference = userReference.document(user.token)

        reference.get()
            .addOnSuccessListener { _user ->
                if (!_user.exists()) {
                    reference.set(user)
                        .addOnSuccessListener { onSuccess(user) }
                        .addOnFailureListener { onError(it) }
                } else {
                    onSuccess(user)
                }
            }
    }

    override fun sendMessage(message: Message, onSuccess: () -> Unit, onError: (Exception) -> Unit) {

        val userList = listOf(message.sender.token, message.receiver.token)
        val channelId = userList.sorted().toString()

        channelReference.document(channelId)
            .collection(MESSAGES_COLLECTION)
            .add(message)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    override fun getUserByToken(token: String, onSuccess: (User) -> Unit, onError: (Exception) -> Unit) {
        userReference
            .get()
            .addOnSuccessListener { result ->
                val data = result.toObjects(User::class.java)
                val user = data.find { it.token == token }
                user?.let { onSuccess(it) }
            }
            .addOnFailureListener { exception -> onError(exception) }
    }

    override fun getOrCreateNewChatChannels(participantUserToken: String, onSuccess: (channelId: String) -> Unit) {

        val userChannelReference = channelReference.document(participantUserToken)
        val currentUser = ChatClient.getInstance().getUser()
        val currentUserToken = currentUser.token
        val currentUserReference = userReference.document(currentUserToken)
        val participantUserReference = userReference.document(participantUserToken)

        val userList = listOf(participantUserToken, currentUserToken)
        val messageId = userList.sorted().toString()

        userChannelReference.get()
            .addOnSuccessListener {
                if (it.exists()) {
                    onSuccess(it[CHANNEL_ID] as String)
                    return@addOnSuccessListener
                }
                getUserByToken(participantUserToken, {
                    channelReference.document(messageId).set(ChannelUsers(currentUser, it))
                }, {

                })
                addChannelsToUser(currentUserReference, participantUserToken, messageId)

                addChannelsToUser(participantUserReference, currentUserToken, messageId)
            }
    }


    private fun addChannelsToUser(reference: DocumentReference, token: String, messageId: String) {
        reference
            .collection(CHANNEL_COLLECTION)
            .document(token)
            .set(mapOf(CHANNEL_ID to messageId))
    }

    override fun getAllChannel(onSuccess: (List<String>) -> Unit) {

        val currentUserToken = ChatClient.getInstance().getUser().token
        val result = mutableListOf<String>()

        channelReference.addSnapshotListener { querySnapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
            if (error != null) {
                return@addSnapshotListener
            }
            querySnapshot?.documents?.forEach {
                if (it.id.contains(currentUserToken)) {
                    result.add(it.id)
                }
            }
            onSuccess(result)
        }
    }

    override fun sendMessageByToken(message: Message, sender: User, receiver: User, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        sendMessage(message, onSuccess, onError)
    }

    override fun getMessages(channelId: String, onSuccess: (List<Message>) -> Unit) {
        val messages = mutableListOf<Message>()
        channelReference.document(channelId)
            .collection(MESSAGES_COLLECTION)
            .addSnapshotListener { querySnapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                querySnapshot?.documents?.forEach {
                    try {
                        val message: Message? = it.toObject(Message::class.java)
                        if (message != null) {
                            messages.add(message)
                        }
                    } catch (e: Exception) {
                    }
                }
                onSuccess(messages)
            }
    }
}