package com.example.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.domain.TaskValidator
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RecurrenceUnit
import com.example.domain.model.Subtask
import com.example.domain.model.Task
import com.example.domain.model.TaskCategory
import com.example.domain.model.TaskHistoryAction
import com.example.domain.model.TaskHistoryEntry
import com.example.domain.model.TaskList
import com.example.domain.model.TaskPriority
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface TaskRepository {
    fun observeTasks(userId: String): Flow<List<Task>>
    suspend fun fetchTasksOnce(userId: String): Result<List<Task>> = Result.success(emptyList())
    fun observeProfile(userId: String): Flow<UserProfile?>
    suspend fun syncUserProfile(userId: String, name: String, email: String): Result<Unit>

    suspend fun createTask(
        userId: String,
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: String,
        category: String,
        startDate: Long? = null,
        startTime: String? = null,
        deadline: Long? = null,
        listId: String? = null,
        isStarred: Boolean = false,
        recurrenceType: String = RecurrenceType.NONE.displayName,
        recurrenceInterval: Int = 1,
        recurrenceUnit: String = RecurrenceUnit.DAYS.displayName,
        subtasks: List<Subtask> = emptyList(),
        sortOrder: Int = 0,
        calendarEventId: String? = null
    ): Result<Task>

    suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: String,
        category: String,
        isCompleted: Boolean,
        startDate: Long? = null,
        startTime: String? = null,
        deadline: Long? = null,
        listId: String? = null,
        isStarred: Boolean = false,
        recurrenceType: String = RecurrenceType.NONE.displayName,
        recurrenceInterval: Int = 1,
        recurrenceUnit: String = RecurrenceUnit.DAYS.displayName,
        subtasks: List<Subtask> = emptyList(),
        sortOrder: Int = 0,
        calendarEventId: String? = null
    ): Result<Unit>

    suspend fun setTaskCompleted(userId: String, taskId: String, isCompleted: Boolean): Result<Unit>
    suspend fun setTaskStarred(userId: String, taskId: String, isStarred: Boolean): Result<Unit> = Result.success(Unit)
    suspend fun updateTaskSubtasks(userId: String, taskId: String, subtasks: List<Subtask>): Result<Unit> = Result.success(Unit)
    suspend fun reorderTasks(userId: String, orderedTaskIds: List<String>): Result<Unit> = Result.success(Unit)
    suspend fun setTaskCalendarEventId(userId: String, taskId: String, calendarEventId: String?): Result<Unit> = Result.success(Unit)
    suspend fun deleteTask(userId: String, taskId: String): Result<Unit>

    // Multiple Task Lists
    fun observeTaskLists(userId: String): Flow<List<TaskList>> = flowOf(emptyList())
    suspend fun createTaskList(userId: String, name: String, colorHex: String): Result<TaskList> =
        Result.failure(UnsupportedOperationException("Task lists unsupported"))
    suspend fun renameTaskList(userId: String, listId: String, newName: String, colorHex: String): Result<Unit> =
        Result.success(Unit)
    suspend fun deleteTaskList(userId: String, listId: String): Result<Unit> = Result.success(Unit)

    // Task History
    fun observeTaskHistory(userId: String): Flow<List<TaskHistoryEntry>> = flowOf(emptyList())
    suspend fun recordTaskHistory(
        userId: String,
        taskId: String,
        taskTitle: String,
        action: TaskHistoryAction,
        details: String? = null
    ): Result<Unit> = Result.success(Unit)
    suspend fun clearTaskHistory(userId: String): Result<Unit> = Result.success(Unit)
}

class FirestoreTaskRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val isOnlineProvider: () -> Boolean = { true }
) : TaskRepository {

    private val listsHistoryDelegate = FirestoreListsHistoryDelegate(
        db = db,
        auth = auth,
        requireAuthorizedUserId = ::requireAuthorizedUserId,
        isOnlineProvider = isOnlineProvider
    )

    constructor(context: Context) : this(
        db = createConfiguredFirestore(context.applicationContext),
        isOnlineProvider = { checkDeviceOnline(context.applicationContext) }
    )

    private fun requireAuthorizedUserId(expectedUserId: String): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(currentUid == expectedUserId) {
            "Signed-in user changed; refusing cross-user operation."
        }
        return currentUid
    }

    override fun observeTasks(userId: String): Flow<List<Task>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("tasks")
                .snapshots()
                .map { querySnapshot ->
                    querySnapshot.documents.mapNotNull { doc -> Task.fromDocument(doc) }
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

    override suspend fun fetchTasksOnce(userId: String): Result<List<Task>> {
        val path = "users/$userId/tasks"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val snapshot = db.collection("users")
                .document(uid)
                .collection("tasks")
                .get()
                .await()
            val tasks = snapshot.documents.mapNotNull { doc -> Task.fromDocument(doc) }
            Result.success(tasks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    override fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(null)
            return@flow
        }
        val path = "users/$userId/profile/info"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("profile")
                .document("info")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null else documentToProfile(snapshot)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun syncUserProfile(
        userId: String,
        name: String,
        email: String
    ): Result<Unit> {
        val docPath = "users/$userId/profile/info"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val sanitizedName = name.trim().ifBlank { "SmartTodo User" }.take(100)
            val sanitizedEmail = email.trim().take(254)
            val docRef = db.collection("users")
                .document(uid)
                .collection("profile")
                .document("info")

            val existingSnapshot = docRef.get().await()
            if (existingSnapshot.exists()) {
                awaitFirestoreWrite(
                    writeTask = docRef.update(
                        mapOf(
                            "name" to sanitizedName,
                            "email" to sanitizedEmail,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ),
                    operationType = OperationType.UPDATE,
                    docPath = docPath,
                    isOnline = isOnlineProvider
                )
            } else {
                awaitFirestoreWrite(
                    writeTask = docRef.set(
                        mapOf(
                            "userId" to uid,
                            "name" to sanitizedName,
                            "email" to sanitizedEmail,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ),
                    operationType = OperationType.CREATE,
                    docPath = docPath,
                    isOnline = isOnlineProvider
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun createTask(
        userId: String,
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: String,
        category: String,
        startDate: Long?,
        startTime: String?,
        deadline: Long?,
        listId: String?,
        isStarred: Boolean,
        recurrenceType: String,
        recurrenceInterval: Int,
        recurrenceUnit: String,
        subtasks: List<Subtask>,
        sortOrder: Int,
        calendarEventId: String?
    ): Result<Task> {
        var docPath = "users/$userId/tasks"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val cleanSubtasks = subtasks.filter { it.title.isNotBlank() }.take(TaskValidator.MAX_SUBTASKS)
            val validation = TaskValidator.validate(
                title = title,
                description = description,
                dueDate = dueDate,
                dueTime = dueTime,
                startDate = startDate,
                startTime = startTime,
                deadline = deadline,
                recurrenceInterval = recurrenceInterval,
                subtasks = cleanSubtasks
            )
            require(validation.isValid) {
                validation.firstErrorMessage() ?: "Invalid task input."
            }

            val nowMillis = System.currentTimeMillis()
            val docRef = db.collection("users")
                .document(uid)
                .collection("tasks")
                .document()
            docPath = docRef.path

            val newTask = Task(
                id = docRef.id,
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                dueDate = dueDate,
                dueTime = dueTime?.trim()?.takeIf { it.isNotEmpty() },
                startDate = startDate,
                startTime = startTime?.trim()?.takeIf { it.isNotEmpty() },
                deadline = deadline,
                priority = TaskPriority.fromRaw(priority).displayName,
                category = TaskCategory.fromRaw(category).displayName,
                listId = listId?.trim()?.takeIf { it.isNotEmpty() },
                isCompleted = false,
                isStarred = isStarred,
                recurrenceType = RecurrenceType.fromRaw(recurrenceType).displayName,
                recurrenceInterval = recurrenceInterval.coerceIn(1, 365),
                recurrenceUnit = RecurrenceUnit.fromRaw(recurrenceUnit).displayName,
                subtasks = cleanSubtasks,
                sortOrder = sortOrder.coerceIn(0, 1_000_000),
                calendarEventId = calendarEventId?.trim()?.take(256)?.takeIf { it.isNotEmpty() },
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
            awaitFirestoreWrite(
                writeTask = docRef.set(newTask.toFirestoreCreateMap(ownerUid = uid)),
                operationType = OperationType.CREATE,
                docPath = docPath,
                isOnline = isOnlineProvider
            )
            Result.success(newTask)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        description: String?,
        dueDate: Long?,
        dueTime: String?,
        priority: String,
        category: String,
        isCompleted: Boolean,
        startDate: Long?,
        startTime: String?,
        deadline: Long?,
        listId: String?,
        isStarred: Boolean,
        recurrenceType: String,
        recurrenceInterval: Int,
        recurrenceUnit: String,
        subtasks: List<Subtask>,
        sortOrder: Int,
        calendarEventId: String?
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank for update." }
            val cleanSubtasks = subtasks.filter { it.title.isNotBlank() }.take(TaskValidator.MAX_SUBTASKS)
            val validation = TaskValidator.validate(
                title = title,
                description = description,
                dueDate = dueDate,
                dueTime = dueTime,
                startDate = startDate,
                startTime = startTime,
                deadline = deadline,
                recurrenceInterval = recurrenceInterval,
                subtasks = cleanSubtasks
            )
            require(validation.isValid) {
                validation.firstErrorMessage() ?: "Invalid task input."
            }

            val updates = mutableMapOf<String, Any>(
                "title" to title.trim().take(TaskValidator.MAX_TITLE_LENGTH),
                "description" to (description?.trim()?.take(TaskValidator.MAX_DESCRIPTION_LENGTH)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                "dueDate" to (dueDate ?: FieldValue.delete()),
                "dueTime" to (dueTime?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                "startDate" to (startDate ?: FieldValue.delete()),
                "startTime" to (startTime?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                "deadline" to (deadline ?: FieldValue.delete()),
                "priority" to TaskPriority.fromRaw(priority).displayName,
                "category" to TaskCategory.fromRaw(category).displayName,
                "listId" to (listId?.trim()?.take(128)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                "isCompleted" to isCompleted,
                "isStarred" to isStarred,
                "recurrenceType" to RecurrenceType.fromRaw(recurrenceType).displayName,
                "recurrenceInterval" to recurrenceInterval.coerceIn(1, 365),
                "recurrenceUnit" to RecurrenceUnit.fromRaw(recurrenceUnit).displayName,
                "subtasks" to cleanSubtasks.map { it.toFirestoreMap() },
                "sortOrder" to sortOrder.coerceIn(0, 1_000_000),
                "calendarEventId" to (calendarEventId?.trim()?.take(256)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            awaitFirestoreWrite(
                writeTask = db.collection("users")
                    .document(uid)
                    .collection("tasks")
                    .document(taskId)
                    .update(updates),
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

    override suspend fun setTaskCompleted(
        userId: String,
        taskId: String,
        isCompleted: Boolean
    ): Result<Unit> = updateTaskFields(
        userId = userId,
        taskId = taskId,
        fields = mapOf("isCompleted" to isCompleted)
    )

    override suspend fun setTaskStarred(
        userId: String,
        taskId: String,
        isStarred: Boolean
    ): Result<Unit> = updateTaskFields(
        userId = userId,
        taskId = taskId,
        fields = mapOf("isStarred" to isStarred)
    )

    override suspend fun updateTaskSubtasks(
        userId: String,
        taskId: String,
        subtasks: List<Subtask>
    ): Result<Unit> = updateTaskFields(
        userId = userId,
        taskId = taskId,
        fields = mapOf(
            "subtasks" to subtasks
                .filter { it.title.isNotBlank() }
                .take(TaskValidator.MAX_SUBTASKS)
                .map { it.toFirestoreMap() }
        )
    )

    override suspend fun setTaskCalendarEventId(
        userId: String,
        taskId: String,
        calendarEventId: String?
    ): Result<Unit> = updateTaskFields(
        userId = userId,
        taskId = taskId,
        fields = mapOf(
            "calendarEventId" to (calendarEventId?.trim()?.take(256)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
        )
    )

    override suspend fun reorderTasks(
        userId: String,
        orderedTaskIds: List<String>
    ): Result<Unit> {
        val path = "users/$userId/tasks"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val validEntries = orderedTaskIds
                .mapIndexedNotNull { index, id -> id.takeIf { it.isNotBlank() }?.let { index to it } }
            if (validEntries.isEmpty()) return Result.success(Unit)
            val tasksCol = db.collection("users").document(uid).collection("tasks")
            validEntries.chunked(FIRESTORE_BATCH_CHUNK_SIZE).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (index, taskId) ->
                    batch.update(
                        tasksCol.document(taskId),
                        mapOf(
                            "sortOrder" to index.coerceIn(0, 1_000_000),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                awaitFirestoreWrite(
                    writeTask = batch.commit(),
                    operationType = OperationType.UPDATE,
                    docPath = path,
                    isOnline = isOnlineProvider
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            awaitFirestoreWrite(
                writeTask = db.collection("users")
                    .document(uid)
                    .collection("tasks")
                    .document(taskId)
                    .delete(),
                operationType = OperationType.DELETE,
                docPath = docPath,
                isOnline = isOnlineProvider
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override fun observeTaskLists(userId: String): Flow<List<TaskList>> =
        listsHistoryDelegate.observeTaskLists(userId)

    override suspend fun createTaskList(userId: String, name: String, colorHex: String): Result<TaskList> =
        listsHistoryDelegate.createTaskList(userId, name, colorHex)

    override suspend fun renameTaskList(
        userId: String,
        listId: String,
        newName: String,
        colorHex: String
    ): Result<Unit> = listsHistoryDelegate.renameTaskList(userId, listId, newName, colorHex)

    override suspend fun deleteTaskList(userId: String, listId: String): Result<Unit> =
        listsHistoryDelegate.deleteTaskList(userId, listId)

    override fun observeTaskHistory(userId: String): Flow<List<TaskHistoryEntry>> =
        listsHistoryDelegate.observeTaskHistory(userId)

    override suspend fun recordTaskHistory(
        userId: String,
        taskId: String,
        taskTitle: String,
        action: TaskHistoryAction,
        details: String?
    ): Result<Unit> = listsHistoryDelegate.recordTaskHistory(userId, taskId, taskTitle, action, details)

    override suspend fun clearTaskHistory(userId: String): Result<Unit> =
        listsHistoryDelegate.clearTaskHistory(userId)

    private suspend fun updateTaskFields(
        userId: String,
        taskId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            val payload = fields + ("updatedAt" to FieldValue.serverTimestamp())
            awaitFirestoreWrite(
                writeTask = db.collection("users")
                    .document(uid)
                    .collection("tasks")
                    .document(taskId)
                    .update(payload),
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

    companion object {
        private const val TAG = "FirestoreTaskRepo"

        @Volatile
        private var cacheConfigured = false

        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            if (!cacheConfigured) {
                synchronized(this) {
                    if (!cacheConfigured) {
                        try {
                            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                                .build()
                        } catch (e: IllegalStateException) {
                            Log.d(TAG, "Firestore settings already configured: ${e.message}")
                        }
                        cacheConfigured = true
                    }
                }
            }
            return firestore
        }

        private fun documentToProfile(doc: DocumentSnapshot): UserProfile {
            return UserProfile(
                userId = doc.getString("userId") ?: "",
                name = doc.getString("name") ?: "",
                email = doc.getString("email") ?: "",
                createdAt = doc.getTimestamp(
                    "createdAt",
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ),
                updatedAt = doc.getTimestamp(
                    "updatedAt",
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        }
    }
}
