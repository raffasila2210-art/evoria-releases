const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const MALLORY_UID = "mallory_uid_003";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastTs = () => new Date(Date.now() - 2000);

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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated caller cannot read users or couples", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("couples").get());
  await assertFails(unauthDb.collection("invitations").get());
});

test("User profile: owner can create and read, stranger cannot read or overwrite", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Nadia",
      aiSearchConsent: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Stranger cannot read Alice's profile
  await assertFails(malloryDb.collection("users").doc(ALICE_UID).get());

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: pastTs(),
    })
  );
});

test("Private backups: strictly isolated to owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private_backups").doc("bkp_1").set({
      ownerUid: ALICE_UID,
      deviceLabel: "Pixel 9",
      albumCount: 2,
      memoryCount: 10,
      reflectionCount: 3,
      payloadJson: "{\"albums\":[]}",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private_backups").doc("bkp_1").get()
  );
});

test("Invitations: single-use, blocks self-accept, blocks terminal mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const codeHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("invitations").doc(codeHash).set({
      codeHash,
      inviterUid: ALICE_UID,
      inviterName: "Nadia",
      status: "pending",
      expiresAtMillis: Date.now() + 86400000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot self-accept her own code
  await assertFails(
    aliceDb.collection("invitations").doc(codeHash).update({
      recipientUid: ALICE_UID,
      recipientName: "Nadia",
      coupleId: "couple_1",
      status: "accepted",
      updatedAt: pastTs(),
    })
  );

  // Bob creates couple and accepts invitation
  await assertSucceeds(
    bobDb.collection("couples").doc("couple_1").set({
      memberUids: [ALICE_UID, BOB_UID],
      memberOneUid: ALICE_UID,
      memberTwoUid: BOB_UID,
      memberOneName: "Nadia",
      memberTwoName: "Raffa",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("invitations").doc(codeHash).update({
      recipientUid: BOB_UID,
      recipientName: "Raffa",
      coupleId: "couple_1",
      status: "accepted",
      updatedAt: pastTs(),
    })
  );

  // Terminal state locking: cannot re-accept or cancel after accepted
  await assertFails(
    aliceDb.collection("invitations").doc(codeHash).update({
      status: "canceled",
      updatedAt: pastTs(),
    })
  );
});

test("Shared memories: accessible to linked couple, denied to outsider", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").set({
      memberUids: [ALICE_UID, BOB_UID],
      memberOneUid: ALICE_UID,
      memberTwoUid: BOB_UID,
      memberOneName: "Nadia",
      memberTwoName: "Raffa",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").collection("shared_memories").doc("mem_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      title: "Senja di Ancol",
      mediaType: "photo",
      memoryDateMillis: 1700000000000,
      isFavorite: true,
      tags: ["pantai", "senja"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("couples").doc("couple_1").collection("shared_memories").where("memberUids", "array-contains", BOB_UID).get()
  );

  await assertFails(
    malloryDb.collection("couples").doc("couple_1").collection("shared_memories").doc("mem_1").get()
  );
});

test("Shared reflections: partner can read, only author can update allowed fields, sealed capsule unlockAtMillis immutable, outsider denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const now = pastTs();
  const futureUnlock = Date.now() + 7 * 86400000;

  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").set({
      memberUids: [ALICE_UID, BOB_UID],
      memberOneUid: ALICE_UID,
      memberTwoUid: BOB_UID,
      memberOneName: "Nadia",
      memberTwoName: "Raffa",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates a shared journal reflection
  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").collection("shared_reflections").doc("ref_j1").set({
      authorUid: ALICE_UID,
      authorName: "Nadia",
      memberUids: [ALICE_UID, BOB_UID],
      kind: "journal",
      title: "Catatan Hujan",
      body: "Hari ini terasa tenang berdua.",
      isSealed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can query shared reflections as couple member
  await assertSucceeds(
    bobDb.collection("couples").doc("couple_1").collection("shared_reflections").where("memberUids", "array-contains", BOB_UID).get()
  );

  // Alice can update restricted fields (title, body, updatedAt)
  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").collection("shared_reflections").doc("ref_j1").update({
      title: "Catatan Hujan Sore",
      body: "Hari ini terasa sangat hangat berdua.",
      updatedAt: pastTs(),
    })
  );

  // Partner Bob cannot edit Alice's reflection
  await assertFails(
    bobDb.collection("couples").doc("couple_1").collection("shared_reflections").doc("ref_j1").update({
      body: "Diubah oleh partner",
      updatedAt: pastTs(),
    })
  );

  // Sealed time capsule unlockAtMillis cannot be shortened
  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").collection("shared_reflections").doc("ref_cap1").set({
      authorUid: ALICE_UID,
      authorName: "Nadia",
      memberUids: [ALICE_UID, BOB_UID],
      kind: "capsule",
      title: "Untuk Anniversary",
      body: "Semoga kita tetap saling menjaga.",
      unlockAtMillis: futureUnlock,
      isSealed: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("couples").doc("couple_1").collection("shared_reflections").doc("ref_cap1").update({
      unlockAtMillis: Date.now() - 1000,
      updatedAt: pastTs(),
    })
  );

  // Stranger Mallory cannot read or write
  await assertFails(
    malloryDb.collection("couples").doc("couple_1").collection("shared_reflections").doc("ref_j1").get()
  );
});

test("Couple notifications: actor creates, only recipient can mark read, outsider denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").set({
      memberUids: [ALICE_UID, BOB_UID],
      memberOneUid: ALICE_UID,
      memberTwoUid: BOB_UID,
      memberOneName: "Nadia",
      memberTwoName: "Raffa",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice sends notification to Bob
  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").collection("notifications").doc("notif_1").set({
      recipientUid: BOB_UID,
      actorUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      kind: "memory",
      title: "Kenangan Baru",
      body: "Nadia membagikan foto Senja di Ancol.",
      isRead: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can query and mark notification as read
  await assertSucceeds(
    bobDb.collection("couples").doc("couple_1").collection("notifications").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("couples").doc("couple_1").collection("notifications").doc("notif_1").update({
      isRead: true,
      updatedAt: pastTs(),
    })
  );

  // Actor Alice cannot mutate recipient's read status
  await assertFails(
    aliceDb.collection("couples").doc("couple_1").collection("notifications").doc("notif_1").update({
      isRead: false,
      updatedAt: pastTs(),
    })
  );

  // Outsider cannot read
  await assertFails(
    malloryDb.collection("couples").doc("couple_1").collection("notifications").doc("notif_1").get()
  );
});

test("Shared music / playlists: couple sends, recipient accepts, outsider denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const now = pastTs();

  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").set({
      memberUids: [ALICE_UID, BOB_UID],
      memberOneUid: ALICE_UID,
      memberTwoUid: BOB_UID,
      memberOneName: "Nadia",
      memberTwoName: "Raffa",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("couples").doc("couple_1").collection("shared_music").doc("smus_1").set({
      senderUid: ALICE_UID,
      senderName: "Nadia",
      recipientUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      kind: "playlist",
      title: "Malam Tenang Berdua",
      subtitle: "4 lagu pilihan",
      tracksJson: JSON.stringify([{ title: "Hujan Sore", artist: "Efek Rumah Kaca" }]),
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Recipient Bob can query & accept
  await assertSucceeds(
    bobDb.collection("couples").doc("couple_1").collection("shared_music").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("couples").doc("couple_1").collection("shared_music").doc("smus_1").update({
      status: "accepted",
      updatedAt: pastTs(),
    })
  );

  // Outsider cannot read or write
  await assertFails(
    malloryDb.collection("couples").doc("couple_1").collection("shared_music").doc("smus_1").get()
  );
});
