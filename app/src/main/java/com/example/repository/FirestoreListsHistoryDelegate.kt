package com.example.repository

import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.domain.TaskValidator
import com.example.domain.model.TaskHistoryAction
import com.example.domain.model.TaskHistoryEntry
import com.example.domain.model.TaskList
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore operations delegate for user-scoped Task Lists (`/users/{uid}/lists`)
 * and append-only Task History (`/users/{uid}/history`).
 */
internal class FirestoreListsHistoryDelegate(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val requireAuthorizedUserId: (String) -> String,
    private val isOnlineProvider: () -> Boolean = { true }
) {
    fun observeTaskLists(userId: String): Flow<List<TaskList>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/lists"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("lists")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> TaskList.fromDocument(doc) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createTaskList(
        userId: String,
        name: String,
        colorHex: String
    ): Result<TaskList> {
        var docPath = "users/$userId/lists"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val nameError = TaskValidator.validateListName(name)
            require(nameError == null) { nameError ?: "Invalid list name." }

            val now = System.currentTimeMillis()
            val docRef = db.collection("users")
                .document(uid)
                .collection("lists")
                .document()
            docPath = docRef.path

            val newList = TaskList(
                id = docRef.id,
                name = name.trim(),
                colorHex = TaskList.normalizeColorHex(colorHex),
                createdAt = now,
                updatedAt = now
            )
            awaitFirestoreWrite(
                writeTask = docRef.set(newList.toFirestoreCreateMap(uid)),
                operationType = OperationType.CREATE,
                docPath = docPath,
                isOnline = isOnlineProvider
            )
            Result.success(newList)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun renameTaskList(
        userId: String,
        listId: String,
        newName: String,
        colorHex: String
    ): Result<Unit> {
        val docPath = "users/$userId/lists/$listId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(listId.isNotBlank()) { "List ID must not be blank." }
            val nameError = TaskValidator.validateListName(newName)
            require(nameError == null) { nameError ?: "Invalid list name." }

            awaitFirestoreWrite(
                writeTask = db.collection("users")
                    .document(uid)
                    .collection("lists")
                    .document(listId)
                    .update(
                        mapOf(
                            "name" to newName.trim().take(TaskList.MAX_NAME_LENGTH),
                            "colorHex" to TaskList.normalizeColorHex(colorHex),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ),
                operationType = OperationType.UPDATE,
                docPath = docPath,
                isOnline = isOnlineProvider
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTaskList(userId: String, listId: String): Result<Unit> {
        val docPath = "users/$userId/lists/$listId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(listId.isNotBlank()) { "List ID must not be blank." }

            val tasksInList = db.collection("users")
                .document(uid)
                .collection("tasks")
                .whereEqualTo("listId", listId)
                .get()
                .await()

            val listRef = db.collection("users")
                .document(uid)
                .collection("lists")
                .document(listId)

            val taskDocs = tasksInList.documents
            if (taskDocs.isEmpty()) {
                awaitFirestoreWrite(
                    writeTask = listRef.delete(),
                    operationType = OperationType.DELETE,
                    docPath = docPath,
                    isOnline = isOnlineProvider
                )
            } else {
                val chunks = taskDocs.chunked(FIRESTORE_BATCH_CHUNK_SIZE)
                chunks.forEachIndexed { chunkIdx, docsChunk ->
                    val batch = db.batch()
                    docsChunk.forEach { taskDoc ->
                        batch.update(
                            taskDoc.reference,
                            mapOf(
                                "listId" to FieldValue.delete(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    if (chunkIdx == chunks.lastIndex) {
                        batch.delete(listRef)
                    }
                    awaitFirestoreWrite(
                        writeTask = batch.commit(),
                        operationType = OperationType.DELETE,
                        docPath = docPath,
                        isOnline = isOnlineProvider
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeTaskHistory(userId: String): Flow<List<TaskHistoryEntry>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/history"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> TaskHistoryEntry.fromDocument(doc) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordTaskHistory(
        userId: String,
        taskId: String,
        taskTitle: String,
        action: TaskHistoryAction,
        details: String?
    ): Result<Unit> {
        var docPath = "users/$userId/history"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val docRef = db.collection("users")
                .document(uid)
                .collection("history")
                .document()
            docPath = docRef.path

            val entry = TaskHistoryEntry(
                id = docRef.id,
                taskId = taskId,
                taskTitle = taskTitle.trim().take(120).ifBlank { "Untitled task" },
                action = action.displayName,
                details = details?.trim()?.take(300)?.takeIf { it.isNotEmpty() },
                timestamp = System.currentTimeMillis()
            )
            awaitFirestoreWrite(
                writeTask = docRef.set(entry.toFirestoreCreateMap(uid)),
                operationType = OperationType.CREATE,
                docPath = docPath,
                isOnline = isOnlineProvider
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearTaskHistory(userId: String): Result<Unit> {
        val path = "users/$userId/history"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val historyCol = db.collection("users").document(uid).collection("history")
            var remainingRounds = 10
            while (remainingRounds > 0) {
                val snapshot = historyCol.limit(FIRESTORE_BATCH_CHUNK_SIZE.toLong()).get().await()
                if (snapshot.isEmpty) break
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                awaitFirestoreWrite(
                    writeTask = batch.commit(),
                    operationType = OperationType.DELETE,
                    docPath = path,
                    isOnline = isOnlineProvider
                )
                if (snapshot.size() < FIRESTORE_BATCH_CHUNK_SIZE) break
                remainingRounds--
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
