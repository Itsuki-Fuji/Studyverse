const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("subjects").get());
  await assertFails(unauthDb.collection("study_groups").get());
  await assertFails(unauthDb.collection("group_invites").doc("MATH101").get());
});

test("UserProfile create, partial update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aadya",
      email: "aadya@example.com",
      course: "B.Tech CS",
      academicYear: "3rd Year",
      institution: "IIT",
      dailyStudyTargetMinutes: 180,
      preferredStudySlot: "MORNING",
      notificationsEnabled: true,
      darkModePref: "SYSTEM",
      currentStreak: 3,
      longestStreak: 5,
      lastStudyDate: "2026-11-16",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update succeeds while preserving createdAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyStudyTargetMinutes: 240,
      darkModePref: "DARK",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field rejected (Shadow Update test)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Subjects, Chapters, Sessions, Deadlines, Notes, Habits, Flashcards, and AI Messages CRUD + Isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Subject
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").set({
      userId: ALICE_UID,
      name: "Algorithms",
      code: "CS301",
      colorHex: "#5B4BDF",
      iconName: "Code",
      priority: "HIGH",
      difficulty: "HARD",
      targetHoursPerWeek: 8.0,
      notes: "Focus on dynamic programming",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject extra `id` field when @DocumentId is used
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_bad").set({
      id: "sub_bad",
      userId: ALICE_UID,
      name: "Math",
      colorHex: "#5B4BDF",
      iconName: "Calculate",
      priority: "MEDIUM",
      difficulty: "MEDIUM",
      targetHoursPerWeek: 4.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Chapter
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chapters").doc("chap_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      title: "Dynamic Programming",
      topics: ["Memoization", "Tabulation"],
      status: "IN_PROGRESS",
      revisionCount: 1,
      estimatedMinutes: 90,
      backlogPriority: "HIGH",
      inBacklog: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial chapter update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chapters").doc("chap_1").update({
      status: "COMPLETED",
      inBacklog: false,
      revisionCount: 2,
      lastRevisedMillis: Date.now(),
      nextRevisionMillis: Date.now() + 86400000 * 3,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // StudySession
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_sessions").doc("sess_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      subjectName: "Algorithms",
      title: "DP Practice",
      dateKey: "2026-11-16",
      startHour: 9,
      startMinute: 0,
      durationMinutes: 25,
      sessionType: "POMODORO",
      completed: true,
      aiGenerated: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Deadline
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("deadlines").doc("dl_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      subjectName: "Algorithms",
      title: "Assignment 3",
      category: "ASSIGNMENT",
      dueAtMillis: Date.now() + 86400000 * 2,
      priority: "HIGH",
      reminderDaysBefore: 1,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Note
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      subjectName: "Algorithms",
      title: "Knapsack Notes",
      content: "0/1 Knapsack recurrence relation...",
      tags: ["DP", "Exam"],
      pinned: true,
      bookmarked: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Habit
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("hab_1").set({
      userId: ALICE_UID,
      title: "Study for 2 hours",
      targetMinutes: 120,
      category: "Focus",
      streak: 1,
      completedDates: ["2026-11-16"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // AI Message
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "USER",
      text: "Explain memoization simply",
      mode: "Explain Like I'm 5",
      subjectName: "Algorithms",
      createdAt: now,
    })
  );

  // Bob cannot list Alice's subjects
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("subjects").get());
  // Alice can list her subjects
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("subjects").get());
});

test("Collaborative StudyGroups: create, invite lookup, join, and group messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("study_groups").doc("grp_1").set({
      name: "CS Finals Squad",
      subjectTag: "Algorithms",
      description: "Daily DP & Graphs prep",
      inviteCode: "ALGO2026",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      dailyGoalMinutes: 120,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("group_invites").doc("ALGO2026").set({
      groupId: "grp_1",
      groupName: "CS Finals Squad",
      ownerId: ALICE_UID,
      createdAt: now,
    })
  );

  // Unfiltered query on study_groups fails; filtered memberUids query succeeds
  await assertFails(aliceDb.collection("study_groups").get());
  await assertSucceeds(
    aliceDb.collection("study_groups").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Bob looks up invite code and joins grp_1
  await assertSucceeds(bobDb.collection("group_invites").doc("ALGO2026").get());
  await assertFails(bobDb.collection("group_invites").get()); // list disabled

  await assertSucceeds(
    bobDb.collection("study_groups").doc("grp_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot hijack ownerId
  await assertFails(
    bobDb.collection("study_groups").doc("grp_1").update({
      ownerId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Group message creation & query
  await assertSucceeds(
    bobDb.collection("study_groups").doc("grp_1").collection("messages").doc("gmsg_1").set({
      groupId: "grp_1",
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Let's solve 3 DP problems today!",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 400),
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("study_groups")
      .doc("grp_1")
      .collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
});
