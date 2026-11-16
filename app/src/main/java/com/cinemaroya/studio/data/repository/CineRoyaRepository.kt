package com.cinemaroya.studio.data.repository

import android.content.Context
import com.cinemaroya.studio.R
import com.cinemaroya.studio.model.GeneratedVideo
import com.cinemaroya.studio.model.VideoProject
import com.cinemaroya.studio.model.VideoScene
import com.cinemaroya.studio.util.OperationType
import com.cinemaroya.studio.util.handleFirestoreError
import com.cinemaroya.studio.util.resolveFirestoreDatabaseId
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CineRoyaRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            resolveFirestoreDatabaseId(context.applicationContext)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProjects(): Flow<List<VideoProject>> = flow {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getProjectById(ownerUid: String, projectId: String): Result<VideoProject?> {
        val path = "users/$ownerUid/projects/$projectId"
        return try {
            val snap = db.collection("users").document(ownerUid)
                .collection("projects").document(projectId)
                .get()
                .await()
            val project = snap.toObject(
                VideoProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createProject(
        title: String,
        synopsis: String,
        genre: String,
        aspectRatio: String,
        visualStyle: String,
        targetDurationSeconds: Long,
        sceneCount: Long,
        globalLightingPreset: String
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.take(120).ifBlank { "مشروع فيديو سينمائي" },
            "synopsis" to synopsis.take(2000),
            "genre" to genre.take(60).ifBlank { "وثائقي سينمائي" },
            "aspectRatio" to if (aspectRatio in listOf("16:9", "9:16", "21:9", "1:1")) aspectRatio else "16:9",
            "visualStyle" to visualStyle.take(80).ifBlank { "Photorealistic 8K IMAX" },
            "targetDurationSeconds" to targetDurationSeconds.coerceIn(5L, 3600L),
            "sceneCount" to sceneCount.coerceIn(0L, 100L),
            "globalLightingPreset" to globalLightingPreset.take(80).ifBlank { "CINEMATIC_GOLD" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProjectMetadata(
        projectId: String,
        title: String,
        synopsis: String,
        targetDurationSeconds: Long,
        sceneCount: Long,
        globalLightingPreset: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
        val updates = mapOf(
            "title" to title.take(120).ifBlank { "مشروع فيديو سينمائي" },
            "synopsis" to synopsis.take(2000),
            "targetDurationSeconds" to targetDurationSeconds.coerceIn(5L, 3600L),
            "sceneCount" to sceneCount.coerceIn(0L, 100L),
            "globalLightingPreset" to globalLightingPreset.take(80).ifBlank { "CINEMATIC_GOLD" },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val uid = requireUserId()
        val projectRef = db.collection("users").document(uid).collection("projects").document(projectId)
        return try {
            val scenesSnap = projectRef.collection("scenes").get().await()
            for (doc in scenesSnap.documents) {
                doc.reference.delete().await()
            }
            projectRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, projectRef.path)
            Result.failure(e)
        }
    }

    fun observeScenes(projectId: String): Flow<List<VideoScene>> = flow {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/projects/$projectId/scenes"
        emitAll(
            db.collection("users").document(uid)
                .collection("projects").document(projectId)
                .collection("scenes")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoScene::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.sceneIndex }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getScenesForProject(projectId: String): List<VideoScene> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val path = "users/$uid/projects/$projectId/scenes"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("projects").document(projectId)
                .collection("scenes")
                .get()
                .await()
            snap.toObjects(
                VideoScene::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedBy { it.sceneIndex }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    suspend fun createScene(
        projectId: String,
        scene: VideoScene
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("projects").document(projectId)
            .collection("scenes").document()
        val payload = mapOf(
            "userId" to uid,
            "projectId" to projectId,
            "sceneIndex" to scene.sceneIndex.coerceIn(0L, 100L),
            "title" to scene.title.take(120).ifBlank { "مشهد سينمائي" },
            "narrationAr" to scene.narrationAr.take(2000),
            "visualPrompt" to scene.visualPrompt.take(2000).ifBlank { "Photorealistic 8K cinematic shot" },
            "durationSeconds" to scene.durationSeconds.coerceIn(3L, 60L),
            "cameraMovement" to if (scene.cameraMovement in listOf("STATIC", "PAN_LEFT", "PAN_RIGHT", "ZOOM_IN", "ZOOM_OUT", "DOLLY_FORWARD", "ORBIT_3D")) scene.cameraMovement else "DOLLY_FORWARD",
            "transitionType" to if (scene.transitionType in listOf("CROSSFADE", "CUT", "DIP_TO_BLACK", "LIGHT_FLASH", "SLIDE_RTL")) scene.transitionType else "CROSSFADE",
            "lightingKeyIntensity" to scene.lightingKeyIntensity.coerceIn(0L, 100L),
            "lightingFillIntensity" to scene.lightingFillIntensity.coerceIn(0L, 100L),
            "lightingRimIntensity" to scene.lightingRimIntensity.coerceIn(0L, 100L),
            "colorTempKelvin" to scene.colorTempKelvin.coerceIn(2000L, 10000L),
            "lightAngleDeg" to scene.lightAngleDeg.coerceIn(0L, 360L),
            "lightElevationDeg" to scene.lightElevationDeg.coerceIn(0L, 90L),
            "atmosphereFog" to scene.atmosphereFog.coerceIn(0L, 100L),
            "colorGradePreset" to scene.colorGradePreset.take(60).ifBlank { "CINEMATIC_GOLD" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateScene(
        projectId: String,
        scene: VideoScene
    ): Result<Unit> {
        val uid = requireUserId()
        require(scene.id.isNotBlank()) { "Scene ID must not be blank for update" }
        val docRef = db.collection("users").document(uid)
            .collection("projects").document(projectId)
            .collection("scenes").document(scene.id)
        val updates = mapOf(
            "sceneIndex" to scene.sceneIndex.coerceIn(0L, 100L),
            "title" to scene.title.take(120).ifBlank { "مشهد سينمائي" },
            "narrationAr" to scene.narrationAr.take(2000),
            "visualPrompt" to scene.visualPrompt.take(2000).ifBlank { "Photorealistic 8K cinematic shot" },
            "durationSeconds" to scene.durationSeconds.coerceIn(3L, 60L),
            "cameraMovement" to if (scene.cameraMovement in listOf("STATIC", "PAN_LEFT", "PAN_RIGHT", "ZOOM_IN", "ZOOM_OUT", "DOLLY_FORWARD", "ORBIT_3D")) scene.cameraMovement else "DOLLY_FORWARD",
            "transitionType" to if (scene.transitionType in listOf("CROSSFADE", "CUT", "DIP_TO_BLACK", "LIGHT_FLASH", "SLIDE_RTL")) scene.transitionType else "CROSSFADE",
            "lightingKeyIntensity" to scene.lightingKeyIntensity.coerceIn(0L, 100L),
            "lightingFillIntensity" to scene.lightingFillIntensity.coerceIn(0L, 100L),
            "lightingRimIntensity" to scene.lightingRimIntensity.coerceIn(0L, 100L),
            "colorTempKelvin" to scene.colorTempKelvin.coerceIn(2000L, 10000L),
            "lightAngleDeg" to scene.lightAngleDeg.coerceIn(0L, 360L),
            "lightElevationDeg" to scene.lightElevationDeg.coerceIn(0L, 90L),
            "atmosphereFog" to scene.atmosphereFog.coerceIn(0L, 100L),
            "colorGradePreset" to scene.colorGradePreset.take(60).ifBlank { "CINEMATIC_GOLD" },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteScene(projectId: String, sceneId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("projects").document(projectId)
            .collection("scenes").document(sceneId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeGeneratedVideos(): Flow<List<GeneratedVideo>> = flow {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/generated_videos"
        emitAll(
            db.collection("users").document(uid)
                .collection("generated_videos")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GeneratedVideo::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveGeneratedVideo(
        projectId: String,
        title: String,
        genre: String,
        aspectRatio: String,
        durationSeconds: Long,
        sceneCount: Long,
        colorGradePreset: String,
        thumbnailBase64: String,
        localFilePath: String,
        videoUrl: String = ""
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("generated_videos").document()
        val payload = mapOf(
            "userId" to uid,
            "projectId" to projectId.take(128).ifBlank { "local_project" },
            "title" to title.take(120).ifBlank { "فيديو سينمائي مُصدّر" },
            "genre" to genre.take(60).ifBlank { "وثائقي سينمائي" },
            "aspectRatio" to if (aspectRatio in listOf("16:9", "9:16", "21:9", "1:1")) aspectRatio else "16:9",
            "durationSeconds" to durationSeconds.coerceIn(3L, 3600L),
            "sceneCount" to sceneCount.coerceIn(1L, 100L),
            "colorGradePreset" to colorGradePreset.take(80).ifBlank { "CINEMATIC_GOLD" },
            "thumbnailBase64" to thumbnailBase64.take(120_000),
            "localFilePath" to localFilePath.take(500),
            "videoUrl" to videoUrl.take(1000),
            "mediaMimeType" to "video/mp4",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteGeneratedVideo(videoId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("generated_videos").document(videoId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}

