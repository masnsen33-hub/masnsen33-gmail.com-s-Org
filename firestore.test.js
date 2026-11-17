const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scanned_documents").get());
  await assertFails(unauthDb.collection("community_words").where("visibility", "==", "COMMUNITY").get());
  await assertFails(
    unauthDb.collection("community_words").doc("word_1").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      visibility: "COMMUNITY",
      tamazightTerm: "Amezruy",
      arabicMeaning: "التاريخ",
      frenchMeaning: "Histoire",
      englishMeaning: "History",
      category: "Society & Culture",
      endorsementsCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("User profile & bookmarks enforce strict owner isolation and schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Create Alice profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      themeMode: "DARK",
      preferredLanguage: "ALL",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Bob cannot read or overwrite Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ themeMode: "LIGHT", updatedAt: new Date(Date.now() - 1000) }));

  // Alice bookmark create, partial update, and shadow-field rejection
  const bmRef = aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("pdf_0005");
  await assertSucceeds(
    bmRef.set({
      entryId: "pdf_0005",
      tamazightTerm: "Isir",
      tamazightPlural: "isiren",
      tifinagh: "ⵉⵙⵉⵔ",
      frenchTerm: "Abolition",
      arabicMeaning: "الإلغاء",
      englishMeaning: "Abolition",
      etymologyNote: "Isir (isiren) : Abolition [1]",
      personalNote: "Key term in chapter 1",
      category: "Governance & Law",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    bmRef.update({
      personalNote: "Updated study note",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost `id` field or Bob reading Alice's bookmark
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bad_bm").set({
      id: "bad_bm",
      entryId: "pdf_0001",
      tamazightTerm: "Tiǧǧin",
      frenchTerm: "Abandon",
      category: "Society & Culture",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
});

test("Scanned documents and extracted_terms enforce Parent Gate and owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("scanned_documents").doc("scan_01");
  const termRef = docRef.collection("extracted_terms").doc("term_01");

  // Orphaned term creation without parent document must fail
  await assertFails(
    termRef.set({
      tamazightTerm: "Tadbelt",
      frenchTerm: "Administration",
      arabicMeaning: "الإدارة",
      englishMeaning: "Administration",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Create parent document then extracted term (mirrors Kotlin flow)
  await assertSucceeds(
    docRef.set({
      title: "Page 11 Scan",
      sourceType: "PDF_PAGE",
      sourcePage: 11,
      rawOcrText: "Administration : Tadbelt (tideblin)",
      detectedTermsCount: 1,
      summaryNote: "Extracted 1 historical term",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    termRef.set({
      tamazightTerm: "Tadbelt",
      tamazightPlural: "tideblin",
      tifinagh: "ⵜⴰⴷⴱⴻⵍⵜ",
      frenchTerm: "Administration",
      arabicMeaning: "الإدارة",
      englishMeaning: "Administration",
      etymologyNote: "From root D-B-L",
      dialectTags: "Kb.",
      createdAt: past,
      updatedAt: past,
    })
  );

  // List queries succeed for owner, fail for Bob
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("scanned_documents").get());
  await assertSucceeds(docRef.collection("extracted_terms").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("scanned_documents").get());
});

test("Community words enforce filtered list queries, author-only edits/deletes, and atomic endorsements", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const wordRefAlice = aliceDb.collection("community_words").doc("word_tilelli");
  await assertSucceeds(
    wordRefAlice.set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      visibility: "COMMUNITY",
      tamazightTerm: "Tilelli",
      tamazightPlural: "tilelliyin",
      tifinagh: "ⵜⵉⵍⴻⵍⵍⵉ",
      arabicMeaning: "الحرية",
      frenchMeaning: "Liberté",
      englishMeaning: "Freedom / Liberty",
      dialect: "Pan-Amazigh",
      category: "Governance & Law",
      exampleNote: "Tilelli i uɣref",
      endorsementsCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Filtered query succeeds; unfiltered query fails
  await assertSucceeds(bobDb.collection("community_words").where("visibility", "==", "COMMUNITY").get());
  await assertFails(bobDb.collection("community_words").get());

  // Bob cannot edit Alice's word text or forge authorUid
  await assertFails(
    bobDb.collection("community_words").doc("word_tilelli").update({
      englishMeaning: "Tampered",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("community_words").doc("word_tilelli").delete()
  );

  // Bob can endorse once via atomic batch creating endorsement doc + incrementing endorsementsCount
  const batch = bobDb.batch();
  const wordRefBob = bobDb.collection("community_words").doc("word_tilelli");
  const endorseRefBob = wordRefBob.collection("endorsements").doc(BOB_UID);
  const now = new Date(Date.now() - 1000);
  batch.set(endorseRefBob, { userId: BOB_UID, createdAt: now });
  batch.update(wordRefBob, { endorsementsCount: 1, updatedAt: now });
  await assertSucceeds(batch.commit());

  // Duplicate endorsement by Bob fails
  await assertFails(
    wordRefBob.update({ endorsementsCount: 2, updatedAt: new Date(Date.now() - 500) })
  );

  // Alice can update her own word while preserving endorsementsCount & createdAt
  await assertSucceeds(
    wordRefAlice.update({
      exampleNote: "Tilelli d azref n yal amdan",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Alice cannot inflate her own endorsementsCount arbitrarily
  await assertFails(
    wordRefAlice.update({
      endorsementsCount: 99,
      updatedAt: new Date(Date.now() - 300),
    })
  );
});
