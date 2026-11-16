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
const PROJECT_DOC_ID = "proj_001";
const SCENE_DOC_ID = "scene_001";
const VIDEO_DOC_ID = "vid_001";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function validProjectPayload(uid, ts) {
  return {
    userId: uid,
    title: "رحلة عبر الصحراء العربية",
    synopsis: "وثائقي سينمائي طويل يستكشف واحات الصحراء العربية قديماً وحديثاً.",
    genre: "وثائقي سينمائي",
    aspectRatio: "16:9",
    visualStyle: "Photorealistic 8K IMAX",
    targetDurationSeconds: 180,
    sceneCount: 4,
    globalLightingPreset: "GOLDEN_HOUR",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validScenePayload(uid, projectId, ts) {
  return {
    userId: uid,
    projectId: projectId,
    sceneIndex: 0,
    title: "شروق الشمس فوق الكثبان",
    narrationAr: "مع أول خيوط الفجر الذهبية، تستيقظ رمال الصحراء على همسات التاريخ.",
    visualPrompt: "Sweeping aerial drone shot of golden Arabian desert dunes at sunrise, volumetric god rays, 8K photorealistic cinema camera",
    durationSeconds: 12,
    cameraMovement: "DOLLY_FORWARD",
    transitionType: "CROSSFADE",
    lightingKeyIntensity: 85,
    lightingFillIntensity: 45,
    lightingRimIntensity: 70,
    colorTempKelvin: 3800,
    lightAngleDeg: 45,
    lightElevationDeg: 25,
    atmosphereFog: 30,
    colorGradePreset: "CINEMATIC_GOLD",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validGeneratedVideoPayload(uid, projectId, ts) {
  return {
    userId: uid,
    projectId: projectId,
    title: "وثائقي الصحراء الذهبية - نسخة نهائية",
    genre: "وثائقي سينمائي",
    aspectRatio: "16:9",
    durationSeconds: 48,
    sceneCount: 4,
    colorGradePreset: "CINEMATIC_GOLD",
    thumbnailBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    localFilePath: "/data/user/0/com.cinemaroya.studio/cache/exported_videos/cineroya_1.mp4",
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user cannot read or list projects or generated videos", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("generated_videos").get()
  );
});

test("Authenticated owner can create, read, list, and update their own project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc(PROJECT_DOC_ID);

  await assertSucceeds(docRef.set(validProjectPayload(ALICE_UID, now)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertSucceeds(
    docRef.update({
      title: "رحلة الصحراء الكبرى - نسخة المخرج",
      sceneCount: 6,
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, or modify Alice's projects, scenes, or generated videos", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc(PROJECT_DOC_ID)
      .set(validProjectPayload(ALICE_UID, now));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc(PROJECT_DOC_ID)
      .collection("scenes")
      .doc(SCENE_DOC_ID)
      .set(validScenePayload(ALICE_UID, PROJECT_DOC_ID, now));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("generated_videos")
      .doc(VIDEO_DOC_ID)
      .set(validGeneratedVideoPayload(ALICE_UID, PROJECT_DOC_ID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").doc(PROJECT_DOC_ID).get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc(PROJECT_DOC_ID)
      .collection("scenes")
      .doc(SCENE_DOC_ID)
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("generated_videos").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("generated_videos").doc(VIDEO_DOC_ID).delete()
  );
});

test("Shadow update & schema validation: rejects ghost fields and invalid lighting values", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc(PROJECT_DOC_ID);
  await assertSucceeds(projectRef.set(validProjectPayload(ALICE_UID, now)));

  // Ghost field on project update
  await assertFails(
    projectRef.update({
      title: "عنوان جديد",
      isVerifiedAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Invalid Kelvin value on scene creation
  const sceneRef = projectRef.collection("scenes").doc(SCENE_DOC_ID);
  const badScene = validScenePayload(ALICE_UID, PROJECT_DOC_ID, now);
  badScene.colorTempKelvin = 99999; // Out of bounds (2000..10000)
  await assertFails(sceneRef.set(badScene));

  // Valid scene creation and lighting update succeeds
  await assertSucceeds(sceneRef.set(validScenePayload(ALICE_UID, PROJECT_DOC_ID, now)));
  await assertSucceeds(
    sceneRef.update({
      lightingKeyIntensity: 95,
      colorTempKelvin: 5600,
      atmosphereFog: 50,
      updatedAt: new Date(),
    })
  );
});

test("GeneratedVideo gallery CRUD & schema bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const videoRef = aliceDb.collection("users").doc(ALICE_UID).collection("generated_videos").doc(VIDEO_DOC_ID);

  // Rejects invalid aspectRatio
  const badVideo = validGeneratedVideoPayload(ALICE_UID, PROJECT_DOC_ID, now);
  badVideo.aspectRatio = "4:3";
  await assertFails(videoRef.set(badVideo));

  // Valid video creation, list, and delete succeeds
  await assertSucceeds(videoRef.set(validGeneratedVideoPayload(ALICE_UID, PROJECT_DOC_ID, now)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("generated_videos").get());
  await assertSucceeds(videoRef.delete());
});
