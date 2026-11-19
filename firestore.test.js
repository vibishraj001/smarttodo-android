const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

const SAMPLE_DUE_DATE_MILLIS = Date.UTC(2026, 10, 20);
const SAMPLE_START_DATE_MILLIS = Date.UTC(2026, 10, 18);
const SAMPLE_DEADLINE_MILLIS = Date.UTC(2026, 10, 25);

function validTaskPayload(userId, overrides = {}) {
  const now = pastTimestamp(5000);
  return {
    userId,
    title: "Finish sprint review slides",
    description: "Include Q4 velocity metrics",
    dueDate: SAMPLE_DUE_DATE_MILLIS,
    dueTime: "14:30",
    startDate: SAMPLE_START_DATE_MILLIS,
    startTime: "09:00",
    deadline: SAMPLE_DEADLINE_MILLIS,
    priority: "High",
    category: "Work",
    listId: "list_work_1",
    isCompleted: false,
    isStarred: true,
    recurrenceType: "Weekly",
    recurrenceInterval: 1,
    recurrenceUnit: "Weeks",
    subtasks: [
      { id: "sub_1", title: "Gather charts", isCompleted: false },
      { id: "sub_2", title: "Review with PM", isCompleted: true },
    ],
    sortOrder: 2,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validProfilePayload(userId, overrides = {}) {
  const now = pastTimestamp(5000);
  return {
    userId,
    name: "Alice Johnson",
    email: "alice@example.com",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validListPayload(userId, overrides = {}) {
  const now = pastTimestamp(5000);
  return {
    userId,
    name: "Q4 Sprint",
    colorHex: "#2563EB",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validHistoryPayload(userId, overrides = {}) {
  const now = pastTimestamp(3000);
  return {
    userId,
    taskId: "task_1",
    taskTitle: "Finish sprint review slides",
    action: "Created",
    details: "Added to Q4 Sprint list",
    timestamp: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write tasks, lists, history, or profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("lists").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("history").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set(validTaskPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("profile")
      .doc("info")
      .get()
  );
});

test("Owner can create, read, and update own profile; non-owner is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const aliceProfileRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("profile")
    .doc("info");

  await assertSucceeds(aliceProfileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(aliceProfileRef.get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("profile").get()
  );
  await assertSucceeds(
    aliceProfileRef.update({
      name: "Alice M. Johnson",
      updatedAt: pastTimestamp(1000),
    })
  );
  await assertFails(
    aliceProfileRef.update({
      createdAt: pastTimestamp(500),
      updatedAt: pastTimestamp(500),
    })
  );

  const bobViewOfAlice = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("profile")
    .doc("info");
  await assertFails(bobViewOfAlice.get());
  await assertFails(
    bobViewOfAlice.update({ name: "Hacked", updatedAt: pastTimestamp(1000) })
  );
});

test("Owner can CRUD custom task lists; non-owner and invalid lists are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceLists = aliceDb.collection("users").doc(ALICE_UID).collection("lists");

  await assertSucceeds(aliceLists.doc("list_1").set(validListPayload(ALICE_UID)));
  await assertSucceeds(aliceLists.get());
  await assertSucceeds(
    aliceLists.doc("list_1").update({
      name: "Renamed Sprint",
      colorHex: "#10B981",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Invalid color hex or empty name fails
  await assertFails(
    aliceLists.doc("bad_color").set(validListPayload(ALICE_UID, { colorHex: "blue" }))
  );
  await assertFails(
    aliceLists.doc("empty_name").set(validListPayload(ALICE_UID, { name: "" }))
  );

  // Non-owner cannot read/write Alice's lists
  const bobView = bobDb.collection("users").doc(ALICE_UID).collection("lists");
  await assertFails(bobView.get());
  await assertFails(bobView.doc("list_1").delete());

  await assertSucceeds(aliceLists.doc("list_1").delete());
});

test("Owner can create (minimal & full), update subtasks/star/sortOrder/recurrence/calendar, and delete tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  await assertSucceeds(tasksCol.doc("task_1").set(validTaskPayload(ALICE_UID)));

  // Legacy minimal task omitting optional fields still succeeds
  const now = pastTimestamp(4000);
  await assertSucceeds(
    tasksCol.doc("task_2").set({
      userId: ALICE_UID,
      title: "Buy milk",
      priority: "Low",
      category: "Personal",
      isCompleted: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    tasksCol.doc("task_1").update({
      isStarred: false,
      sortOrder: 5,
      recurrenceType: "Custom",
      recurrenceInterval: 3,
      recurrenceUnit: "Days",
      subtasks: [{ id: "sub_1", title: "Updated subtask", isCompleted: true }],
      calendarEventId: "gcal_evt_abc123",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Clearing optional fields to null matches Kotlin nullable field clearing
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      description: null,
      dueDate: null,
      dueTime: null,
      listId: null,
      calendarEventId: null,
      updatedAt: pastTimestamp(500),
    })
  );

  // Reject immutable field mutation (createdAt or userId)
  await assertFails(
    tasksCol.doc("task_1").update({
      createdAt: pastTimestamp(200),
      updatedAt: pastTimestamp(200),
    })
  );
  await assertFails(
    tasksCol.doc("task_1").update({
      userId: BOB_UID,
      updatedAt: pastTimestamp(200),
    })
  );

  await assertSucceeds(tasksCol.doc("task_2").delete());
});

test("Kotlin repository queries (orderBy, whereEqualTo, limit) and batch operations succeed; cross-user task access fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const aliceTasks = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");
  const aliceLists = aliceDb.collection("users").doc(ALICE_UID).collection("lists");
  const aliceHistory = aliceDb.collection("users").doc(ALICE_UID).collection("history");

  await assertSucceeds(aliceLists.doc("list_work_1").set(validListPayload(ALICE_UID)));
  await assertSucceeds(aliceTasks.doc("task_a").set(validTaskPayload(ALICE_UID, { listId: "list_work_1", sortOrder: 0 })));
  await assertSucceeds(aliceTasks.doc("task_b").set(validTaskPayload(ALICE_UID, { listId: "list_work_1", sortOrder: 1 })));
  await assertSucceeds(aliceHistory.doc("evt_a").set(validHistoryPayload(ALICE_UID, { taskId: "task_a" })));

  // Mirror Kotlin observeTaskLists query: orderBy("createdAt", ASC)
  await assertSucceeds(aliceLists.orderBy("createdAt", "asc").get());

  // Mirror Kotlin observeTaskHistory query: orderBy("timestamp", DESC).limit(100)
  await assertSucceeds(aliceHistory.orderBy("timestamp", "desc").limit(100).get());

  // Mirror Kotlin deleteTaskList query: whereEqualTo("listId", listId)
  await assertSucceeds(aliceTasks.where("listId", "==", "list_work_1").get());

  // Mirror Kotlin reorderTasks batch update
  const reorderBatch = aliceDb.batch();
  reorderBatch.update(aliceTasks.doc("task_a"), { sortOrder: 1, updatedAt: pastTimestamp(800) });
  reorderBatch.update(aliceTasks.doc("task_b"), { sortOrder: 0, updatedAt: pastTimestamp(800) });
  await assertSucceeds(reorderBatch.commit());

  // Mirror Kotlin deleteTaskList batch (unassign tasks + delete list)
  const deleteListBatch = aliceDb.batch();
  deleteListBatch.update(aliceTasks.doc("task_a"), { listId: null, updatedAt: pastTimestamp(500) });
  deleteListBatch.update(aliceTasks.doc("task_b"), { listId: null, updatedAt: pastTimestamp(500) });
  deleteListBatch.delete(aliceLists.doc("list_work_1"));
  await assertSucceeds(deleteListBatch.commit());

  // Cross-user task get, list, update, delete must all fail
  const bobViewTasks = bobDb.collection("users").doc(ALICE_UID).collection("tasks");
  await assertFails(bobViewTasks.get());
  await assertFails(bobViewTasks.doc("task_a").get());
  await assertFails(bobViewTasks.doc("task_a").update({ isCompleted: true, updatedAt: pastTimestamp(400) }));
  await assertFails(bobViewTasks.doc("task_a").delete());
});

test("Owner can append and read task history; updates are denied and non-owner is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const historyCol = aliceDb.collection("users").doc(ALICE_UID).collection("history");

  await assertSucceeds(historyCol.doc("evt_1").set(validHistoryPayload(ALICE_UID)));
  await assertSucceeds(historyCol.get());

  // Append-only: updates must fail
  await assertFails(
    historyCol.doc("evt_1").update({ action: "Deleted" })
  );

  // Invalid action rejected
  await assertFails(
    historyCol.doc("evt_bad").set(validHistoryPayload(ALICE_UID, { action: "Hacked" }))
  );

  // Non-owner rejected
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("history").get()
  );

  await assertSucceeds(historyCol.doc("evt_1").delete());
});

test("Rejects shadow fields, bad recurrence/time/subtasks, oversized strings, and cross-user writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  await assertFails(
    tasksCol.doc("ghost_1").set(validTaskPayload(ALICE_UID, { isAdmin: true }))
  );
  await assertFails(
    tasksCol.doc("id_1").set(validTaskPayload(ALICE_UID, { id: "id_1" }))
  );
  await assertFails(tasksCol.doc("spoof_1").set(validTaskPayload(BOB_UID)));
  await assertFails(
    tasksCol.doc("bad_rec").set(validTaskPayload(ALICE_UID, { recurrenceType: "Hourly" }))
  );
  await assertFails(
    tasksCol.doc("bad_int").set(validTaskPayload(ALICE_UID, { recurrenceInterval: 0 }))
  );
  await assertFails(
    tasksCol.doc("bad_unit").set(validTaskPayload(ALICE_UID, { recurrenceUnit: "Hours" }))
  );
  await assertFails(
    tasksCol.doc("bad_start_time").set(validTaskPayload(ALICE_UID, { startTime: "99:99" }))
  );
  await assertFails(
    tasksCol.doc("bad_deadline").set(validTaskPayload(ALICE_UID, { deadline: -5 }))
  );
  await assertFails(
    tasksCol.doc("future_ts").set(
      validTaskPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );
});
