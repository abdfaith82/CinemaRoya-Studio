package com.cinemaroya.studio.data.repository

import com.cinemaroya.studio.base.FirestoreEmulatorTestBase
import com.cinemaroya.studio.model.VideoScene
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CineRoyaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createProjectAndScene_authenticatedOwner_succeedsAndEmitsUpdates() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CineRoyaRepository(firestore)

        val projectResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(
                title = "ملحمة الأندلس المفقودة",
                synopsis = "فيلم وثائقي درامي طويل يستعرض العمارة والعلوم في قرطبة.",
                genre = "تاريخي ملحمي",
                aspectRatio = "16:9",
                visualStyle = "Photorealistic 8K IMAX",
                targetDurationSeconds = 180L,
                sceneCount = 1L,
                globalLightingPreset = "CINEMATIC_GOLD"
            )
        }
        assertTrue(projectResult.isSuccess)
        val projectId = projectResult.getOrThrow()

        val sceneResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createScene(
                projectId = projectId,
                scene = VideoScene(
                    sceneIndex = 0L,
                    title = "قصر الزهراء عند الغروب",
                    narrationAr = "تتلألأ أعمدة الرخام تحت أشعة الشمس الذهبية.",
                    visualPrompt = "Wide cinematic shot of Medina Azahara palace arches at sunset, golden volumetric rays",
                    durationSeconds = 15L,
                    cameraMovement = "DOLLY_FORWARD",
                    transitionType = "CROSSFADE",
                    lightingKeyIntensity = 90L,
                    lightingFillIntensity = 50L,
                    lightingRimIntensity = 80L,
                    colorTempKelvin = 3600L,
                    lightAngleDeg = 40L,
                    lightElevationDeg = 25L,
                    atmosphereFog = 30L,
                    colorGradePreset = "CINEMATIC_GOLD"
                )
            )
        }
        assertTrue(sceneResult.isSuccess)
        val sceneId = sceneResult.getOrThrow()

        val observedProjects = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProjects().first { list -> list.any { it.id == projectId } }
        }
        assertTrue(observedProjects.any { it.id == projectId })

        val observedScenes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeScenes(projectId).first { list -> list.any { it.id == sceneId } }
        }
        assertTrue(observedScenes.any { it.id == sceneId })
    }

    @Test
    fun getProjectById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CineRoyaRepository(firestore)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(
                title = "مشروع خاص بأليس",
                synopsis = "سيناريو خاص",
                genre = "دراما",
                aspectRatio = "16:9",
                visualStyle = "8K",
                targetDurationSeconds = 60L,
                sceneCount = 0L,
                globalLightingPreset = "CINEMATIC_GOLD"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CineRoyaRepository(firestore)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getProjectById(aliceUid, projectId)
        }
        assertTrue(crossResult.isFailure)
        val fsEx = crossResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CineRoyaRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeProjects().first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val fsEx = caughtException?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun saveAndObserveGeneratedVideo_authenticatedOwner_succeedsAndUnauthFails() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CineRoyaRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveGeneratedVideo(
                projectId = "proj_gallery_1",
                title = "فيلم العلا المُصدّر",
                genre = "وثائقي سينمائي",
                aspectRatio = "16:9",
                durationSeconds = 48L,
                sceneCount = 4L,
                colorGradePreset = "CINEMATIC_GOLD",
                thumbnailBase64 = "AAAA",
                localFilePath = "/cache/video_1.mp4"
            )
        }
        assertTrue(saveResult.isSuccess)
        val videoId = saveResult.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeGeneratedVideos().first { list -> list.any { it.id == videoId } }
        }
        assertTrue(observed.any { it.id == videoId && it.title == "فيلم العلا المُصدّر" })

        auth.signOut()
        var caughtUnauth: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeGeneratedVideos().first()
            }
        } catch (e: Throwable) {
            caughtUnauth = e
        }
        assertNotNull(caughtUnauth)
        val unauthFsEx = caughtUnauth?.unwrapFirestoreException()
        assertNotNull(unauthFsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, unauthFsEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@cineroya.test"
        const val BOB_EMAIL = "bob@cineroya.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}

