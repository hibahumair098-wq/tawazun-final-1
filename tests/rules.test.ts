import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment
} from "@firebase/rules-unit-testing";
import * as fs from "fs";
import * as path from "path";

describe("Tawazun Security Rules Unit Tests", () => {
  let testEnv: RulesTestEnvironment;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "tawazun-rules-test",
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, "../firestore.rules"), "utf8"),
        host: "localhost",
        port: 8080
      }
    });
  });

  afterAll(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  test("Signed-out read is denied across the board", async () => {
    const unauthedDb = testEnv.unauthenticatedContext().firestore();
    const familyDoc = unauthedDb.doc("families/fam_pk_001");
    await assertFails(familyDoc.get());
  });

  test("Cross-family document read returns PERMISSION_DENIED", async () => {
    // Authenticated as Family 2 Parent
    const fam2ParentDb = testEnv.authenticatedContext("user_tariq", {
      role: "PARENT",
      familyId: "fam_pk_002"
    }).firestore();

    // Tries to read Family 1 child document
    const fam1ChildDoc = fam2ParentDb.doc("families/fam_pk_001/children/child_zain");
    await assertFails(fam1ChildDoc.get());
  });

  test("Same-family parent can read their own family documents", async () => {
    // Seed Family 1
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc("families/fam_pk_001/children/child_zain").set({
        name: "Zain Ali",
        grade: "Grade 9"
      });
    });

    const fam1ParentDb = testEnv.authenticatedContext("user_ahmed", {
      role: "PARENT",
      familyId: "fam_pk_001"
    }).firestore();

    const childDoc = fam1ParentDb.doc("families/fam_pk_001/children/child_zain");
    await assertSucceeds(childDoc.get());
  });

  test("Child cannot write app policy; only parent can write policy", async () => {
    const childDb = testEnv.authenticatedContext("child_zain", {
      role: "STUDENT",
      familyId: "fam_pk_001"
    }).firestore();

    const policyDoc = childDb.doc("families/fam_pk_001/children/child_zain/policies/youtube");
    await assertFails(policyDoc.set({ dailyLimitMin: 240 }));

    const parentDb = testEnv.authenticatedContext("user_ahmed", {
      role: "PARENT",
      familyId: "fam_pk_001"
    }).firestore();

    await assertSucceeds(policyDoc.set({ dailyLimitMin: 45 }));
  });

  test("Client/device cannot write directly to usageEvents or webAttempts", async () => {
    const parentDb = testEnv.authenticatedContext("user_ahmed", {
      role: "PARENT",
      familyId: "fam_pk_001"
    }).firestore();

    const usageDoc = parentDb.collection("usageEvents").doc("fake_event");
    await assertFails(usageDoc.set({
      childId: "child_zain",
      familyId: "fam_pk_001",
      appKey: "youtube",
      durationMin: 120
    }));

    const webDoc = parentDb.collection("webAttempts").doc("fake_attempt");
    await assertFails(webDoc.set({
      childId: "child_zain",
      familyId: "fam_pk_001",
      domain: "youtube.com",
      blocked: false
    }));
  });
});
