package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class GeneratedInvitation(
  val rawCode: String,
  val codeHash: String,
  val expiresAtMillis: Long,
)

class EvoriaCloudRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
  )

  private val profileUpsertMutex = Mutex()

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")

  fun hashInvitationCode(rawCode: String): String {
    val normalized = rawCode.trim().uppercase().replace("-", "").replace(" ", "")
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun generateSecureCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    return buildString(6) {
      repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
  }

  // --- USER PROFILE ---

  fun observeUserProfile(uid: String): Flow<UserProfileDto?> =
    db.collection("users")
      .document(uid)
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(UserProfileDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.GET, "users/$uid", auth)
        }
        throw error
      }

  suspend fun upsertUserProfile(
    displayName: String,
    partnerNickname: String? = null,
    avatarUrl: String? = null,
    aiSearchConsent: Boolean? = null,
  ): Result<Unit> = runCatching {
    profileUpsertMutex.withLock {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid)
      val existing =
        try {
          docRef.get(Source.SERVER).await()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          docRef.get().await()
        }
      if (!existing.exists()) {
        val payload =
          mapOf(
              "userId" to uid,
              "displayName" to displayName.trim().ifBlank { "Sahabat EVORIA" }.take(80),
              "partnerNickname" to partnerNickname?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
              "avatarUrl" to avatarUrl?.takeIf { it.isNotBlank() }?.take(500),
              "aiSearchConsent" to (aiSearchConsent ?: false),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
            .filterNonNull()
        try {
          docRef.set(payload).await()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
          throw e
        }
      } else {
        val updates =
          mapOf(
              "displayName" to displayName.trim().ifBlank { "Sahabat EVORIA" }.take(80),
              "partnerNickname" to partnerNickname?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
              "avatarUrl" to avatarUrl?.takeIf { it.isNotBlank() }?.take(500),
              "aiSearchConsent" to aiSearchConsent,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
            .filterNonNull()
        try {
          docRef.update(updates).await()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
          throw e
        }
      }
    }
  }

  suspend fun syncOwnPartnerSummary(
    coupleId: String?,
    partnerUid: String?,
    partnerDisplayName: String?,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val updates =
      mapOf(
        "coupleId" to coupleId,
        "partnerUid" to partnerUid,
        "partnerDisplayName" to partnerDisplayName,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  // --- COUPLE CONNECTIONS ---

  fun observeActiveCouple(uid: String): Flow<CoupleConnectionDto?> =
    db.collection("couples")
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snap ->
        snap.documents
          .mapNotNull {
            it.toObject(
              CoupleConnectionDto::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          }
          .firstOrNull { it.status == "active" }
      }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.LIST, "couples", auth)
        }
        throw error
      }

  suspend fun disconnectCouple(coupleId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("couples").document(coupleId)
    val userRef = db.collection("users").document(uid)
    try {
      val coupleSnap = docRef.get().await()
      if (coupleSnap.exists() && coupleSnap.getString("status") == "active") {
        docRef
          .update(
            mapOf(
              "status" to "disconnected",
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      val userSnap = userRef.get().await()
      if (userSnap.exists()) {
        userRef
          .update(
            mapOf(
              "coupleId" to null,
              "partnerUid" to null,
              "partnerDisplayName" to null,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  // --- INVITATION CODES (6-char, hashed, single-use, 24h expiry, atomic redemption) ---

  fun observeMyInvitations(uid: String): Flow<List<InvitationDto>> =
    db.collection("invitations")
      .whereEqualTo("inviterUid", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(
          InvitationDto::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
          handleFirestoreError(fsErr ?: error, OperationType.LIST, "invitations", auth)
        }
        throw error
      }

  suspend fun createInvitation(inviterName: String): Result<GeneratedInvitation> = runCatching {
    val uid = requireUserId()
    // Ensure user is not already linked to a partner
    val activeCouple =
      db.collection("couples")
        .whereArrayContains("memberUids", uid)
        .get()
        .await()
        .toObjects(CoupleConnectionDto::class.java)
        .firstOrNull { it.status == "active" }
    require(activeCouple == null) {
      "Akun Anda sudah terhubung dengan satu pasangan. Putuskan koneksi terlebih dahulu untuk membuat kode baru."
    }

    val rawCode = generateSecureCode()
    val codeHash = hashInvitationCode(rawCode)
    val expiresAtMillis = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
    val docRef = db.collection("invitations").document(codeHash)
    val payload =
      mapOf(
        "codeHash" to codeHash,
        "inviterUid" to uid,
        "inviterName" to inviterName.trim().ifBlank { "Pasangan EVORIA" }.take(80),
        "status" to "pending",
        "expiresAtMillis" to expiresAtMillis,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      GeneratedInvitation(rawCode = rawCode, codeHash = codeHash, expiresAtMillis = expiresAtMillis)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun lookupInvitation(rawCode: String): Result<InvitationDto> = runCatching {
    val uid = requireUserId()
    val normalized = rawCode.trim().uppercase().replace("-", "").replace(" ", "")
    require(normalized.length == 6) { "Kode undangan harus terdiri dari 6 karakter." }
    val codeHash = hashInvitationCode(normalized)
    val docRef = db.collection("invitations").document(codeHash)
    val snap =
      try {
        docRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path, auth)
        throw e
      }
    if (!snap.exists()) {
      throw IllegalArgumentException("Kode undangan tidak ditemukan atau tidak valid.")
    }
    val invite =
      snap.toObject(InvitationDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: throw IllegalStateException("Data undangan tidak terbaca.")
    if (invite.inviterUid == uid) {
      throw IllegalArgumentException("Anda tidak dapat menautkan kode undangan milik sendiri.")
    }
    if (invite.status == "canceled") {
      throw IllegalStateException("Undangan ini telah dibatalkan oleh pengirim.")
    }
    if (invite.status == "accepted" || invite.status == "declined") {
      throw IllegalStateException("Kode undangan ini sudah pernah digunakan.")
    }
    if (invite.expiresAtMillis <= System.currentTimeMillis()) {
      throw IllegalStateException("Kode undangan telah kedaluwarsa (melebihi 24 jam).")
    }
    invite
  }

  suspend fun cancelInvitation(codeHash: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("invitations").document(codeHash)
    try {
      docRef
        .update(
          mapOf(
            "status" to "canceled",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun declineInvitation(codeHash: String, recipientName: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("invitations").document(codeHash)
    try {
      db.runTransaction { tx ->
          val snap = tx.get(docRef)
          val invite =
            snap.toObject(InvitationDto::class.java)
              ?: throw IllegalStateException("Undangan tidak ditemukan.")
          require(invite.inviterUid != uid) { "Tidak dapat menolak kode sendiri." }
          require(invite.status == "pending") { "Undangan tidak lagi berstatus menunggu." }
          tx.update(
            docRef,
            mapOf(
              "recipientUid" to uid,
              "recipientName" to recipientName.trim().ifBlank { "Pasangan" }.take(80),
              "status" to "declined",
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun acceptInvitation(codeHash: String, recipientName: String): Result<CoupleConnectionDto> =
    runCatching {
      val uid = requireUserId()
      val existingCouple =
        db.collection("couples")
          .whereArrayContains("memberUids", uid)
          .get()
          .await()
          .toObjects(CoupleConnectionDto::class.java)
          .firstOrNull { it.status == "active" }
      require(existingCouple == null) {
        "Akun Anda sudah terhubung dengan pasangan aktif."
      }

      val inviteRef = db.collection("invitations").document(codeHash)
      val coupleId = "couple_${UUID.randomUUID().toString().replace("-", "").take(16)}"
      val coupleRef = db.collection("couples").document(coupleId)
      val cleanRecipientName = recipientName.trim().ifBlank { "Pasangan EVORIA" }.take(80)

      val createdCouple =
        try {
          db.runTransaction { tx ->
              val snap = tx.get(inviteRef)
              if (!snap.exists()) {
                throw IllegalArgumentException("Undangan tidak ditemukan.")
              }
              val invite =
                snap.toObject(InvitationDto::class.java)
                  ?: throw IllegalStateException("Undangan tidak valid.")
              require(invite.inviterUid != uid) {
                "Tidak dapat menautkan akun ke diri sendiri."
              }
              require(invite.status == "pending") {
                "Kode undangan sudah digunakan atau dibatalkan."
              }
              require(invite.expiresAtMillis > System.currentTimeMillis()) {
                "Kode undangan sudah kedaluwarsa."
              }

              val members = listOf(invite.inviterUid, uid)
              tx.set(
                coupleRef,
                mapOf(
                  "memberUids" to members,
                  "memberOneUid" to invite.inviterUid,
                  "memberTwoUid" to uid,
                  "memberOneName" to invite.inviterName,
                  "memberTwoName" to cleanRecipientName,
                  "anniversaryLabel" to "Hari pertama di EVORIA",
                  "status" to "active",
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
              tx.update(
                inviteRef,
                mapOf(
                  "recipientUid" to uid,
                  "recipientName" to cleanRecipientName,
                  "coupleId" to coupleId,
                  "status" to "accepted",
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
              CoupleConnectionDto(
                id = coupleId,
                memberUids = members,
                memberOneUid = invite.inviterUid,
                memberTwoUid = uid,
                memberOneName = invite.inviterName,
                memberTwoName = cleanRecipientName,
                anniversaryLabel = "Hari pertama di EVORIA",
                status = "active",
              )
            }
            .await()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          handleFirestoreError(e, OperationType.WRITE, inviteRef.path, auth)
          throw e
        }

      syncOwnPartnerSummary(
        coupleId = createdCouple.id,
        partnerUid = createdCouple.memberOneUid,
        partnerDisplayName = createdCouple.memberOneName,
      )
      createdCouple
    }

  // --- SHARED MEMORIES, ALBUMS, SHARE REQUESTS, REFLECTIONS, NOTIFICATIONS ---

  fun observeSharedAlbums(coupleId: String): Flow<List<SharedAlbumDto>> = flow {
    val uid = requireUserId()
    val path = "couples/$coupleId/shared_albums"
    emitAll(
      db.collection("couples")
        .document(coupleId)
        .collection("shared_albums")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(
            SharedAlbumDto::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val fsErr = error.unwrapFirestoreException()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun publishSharedAlbum(
    coupleId: String,
    memberUids: List<String>,
    albumId: String,
    title: String,
    description: String?,
    soundtrackTitle: String?,
    itemCount: Long,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("couples").document(coupleId).collection("shared_albums").document(albumId)
    val payload =
      mapOf(
          "ownerUid" to uid,
          "memberUids" to memberUids,
          "title" to title.trim().take(120),
          "description" to description?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
          "soundtrackTitle" to soundtrackTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
          "itemCount" to itemCount.coerceAtLeast(0L),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        .filterNonNull()
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  fun observeSharedMemories(coupleId: String): Flow<List<SharedMemoryDto>> = flow {
    val uid = requireUserId()
    val path = "couples/$coupleId/shared_memories"
    emitAll(
      db.collection("couples")
        .document(coupleId)
        .collection("shared_memories")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(
            SharedMemoryDto::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val fsErr = error.unwrapFirestoreException()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun publishSharedMemory(
    coupleId: String,
    memberUids: List<String>,
    memoryId: String,
    albumId: String?,
    title: String,
    caption: String?,
    story: String?,
    locationName: String?,
    mediaType: String,
    memoryDateMillis: Long,
    isFavorite: Boolean,
    tags: List<String>,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef =
      db.collection("couples").document(coupleId).collection("shared_memories").document(memoryId)
    val payload =
      mapOf(
          "ownerUid" to uid,
          "memberUids" to memberUids,
          "albumId" to albumId?.takeIf { it.isNotBlank() },
          "title" to title.trim().ifBlank { "Kenangan Bersama" }.take(160),
          "caption" to caption?.trim()?.takeIf { it.isNotEmpty() }?.take(600),
          "story" to story?.trim()?.takeIf { it.isNotEmpty() }?.take(4000),
          "locationName" to locationName?.trim()?.takeIf { it.isNotEmpty() }?.take(140),
          "mediaType" to mediaType,
          "memoryDateMillis" to memoryDateMillis,
          "isFavorite" to isFavorite,
          "tags" to tags.take(12),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        .filterNonNull()
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  fun observeShareRequests(coupleId: String): Flow<List<SharedContentRequestDto>> = flow {
    val uid = requireUserId()
    val path = "couples/$coupleId/share_requests"
    emitAll(
      db.collection("couples")
        .document(coupleId)
        .collection("share_requests")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(
            SharedContentRequestDto::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val fsErr = error.unwrapFirestoreException()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun createShareRequest(
    coupleId: String,
    memberUids: List<String>,
    senderName: String,
    recipientUid: String,
    memoryId: String?,
    albumId: String?,
    title: String,
    caption: String?,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val requestId = "req_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val docRef =
      db.collection("couples").document(coupleId).collection("share_requests").document(requestId)
    val payload =
      mapOf(
          "senderUid" to uid,
          "senderName" to senderName.trim().ifBlank { "Pasangan" }.take(80),
          "recipientUid" to recipientUid,
          "memberUids" to memberUids,
          "memoryId" to memoryId?.takeIf { it.isNotBlank() },
          "albumId" to albumId?.takeIf { it.isNotBlank() },
          "title" to title.trim().take(160),
          "caption" to caption?.trim()?.takeIf { it.isNotEmpty() }?.take(600),
          "status" to "pending",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        .filterNonNull()
    try {
      docRef.set(payload).await()
      requestId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun respondToShareRequest(
    coupleId: String,
    requestId: String,
    newStatus: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    require(newStatus in listOf("accepted", "declined", "revoked")) {
      "Status share request tidak valid: $newStatus"
    }
    val docRef =
      db.collection("couples").document(coupleId).collection("share_requests").document(requestId)
    try {
      docRef
        .update(
          mapOf(
            "status" to newStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  fun observeSharedMusic(coupleId: String): Flow<List<SharedMusicDto>> = flow {
    val uid = requireUserId()
    val path = "couples/$coupleId/shared_music"
    emitAll(
      db.collection("couples")
        .document(coupleId)
        .collection("shared_music")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              SharedMusicDto::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
        }
        .catch { error ->
          val fsErr = error.unwrapFirestoreException()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun sendSharedMusic(
    coupleId: String,
    memberUids: List<String>,
    senderName: String,
    recipientUid: String,
    kind: String,
    title: String,
    subtitle: String?,
    externalUrl: String?,
    coverUrl: String?,
    tracksJson: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    require(coupleId.isNotBlank() && recipientUid.isNotBlank() && uid != recipientUid && memberUids.size == 2) {
      "Data pasangan tidak valid untuk mengirim musik."
    }
    require(kind == "track" || kind == "playlist") { "Tipe kiriman musik tidak valid." }
    val shareId = "smus_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val docRef =
      db.collection("couples").document(coupleId).collection("shared_music").document(shareId)
    val payload =
      mapOf(
          "senderUid" to uid,
          "senderName" to senderName.trim().ifBlank { "Pasangan EVORIA" }.take(80),
          "recipientUid" to recipientUid,
          "memberUids" to memberUids,
          "kind" to kind,
          "title" to title.trim().ifBlank { "Kiriman Musik" }.take(160),
          "subtitle" to subtitle?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
          "externalUrl" to externalUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
          "coverUrl" to coverUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
          "tracksJson" to tracksJson.take(40_000),
          "status" to "pending",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        .filterNonNull()
    try {
      docRef.set(payload).await()
      shareId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun respondToSharedMusic(
    coupleId: String,
    shareId: String,
    newStatus: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    require(newStatus in listOf("accepted", "declined", "revoked")) {
      "Status kiriman musik tidak valid: $newStatus"
    }
    val docRef =
      db.collection("couples").document(coupleId).collection("shared_music").document(shareId)
    try {
      docRef
        .update(
          mapOf(
            "status" to newStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  fun observeSharedReflections(coupleId: String): Flow<List<SharedReflectionDto>> = flow {
    val uid = requireUserId()
    val path = "couples/$coupleId/shared_reflections"
    emitAll(
      db.collection("couples")
        .document(coupleId)
        .collection("shared_reflections")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              SharedReflectionDto::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
        }
        .catch { error ->
          val fsErr = error.unwrapFirestoreException()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun publishSharedReflection(
    coupleId: String,
    memberUids: List<String>,
    reflectionId: String,
    authorName: String,
    kind: String,
    title: String,
    body: String,
    unlockAtMillis: Long? = null,
    isSealed: Boolean = false,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(kind in listOf("journal", "letter", "capsule")) {
      "Jenis refleksi bersama tidak valid: $kind"
    }
    val cleanTitle = title.trim().ifBlank { "Refleksi Bersama" }.take(160)
    val cleanBody = body.trim().ifBlank { cleanTitle }.take(8000)
    val docRef =
      db.collection("couples")
        .document(coupleId)
        .collection("shared_reflections")
        .document(reflectionId)

    val existingSnap =
      try {
        docRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }

    if (existingSnap?.exists() == true) {
      val updates =
        mapOf(
          "title" to cleanTitle,
          "body" to cleanBody,
          "isSealed" to isSealed,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      try {
        docRef.update(updates).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        throw e
      }
    } else {
      val payload =
        mapOf(
            "authorUid" to uid,
            "authorName" to authorName.trim().ifBlank { "Pasangan EVORIA" }.take(80),
            "memberUids" to memberUids,
            "kind" to kind,
            "title" to cleanTitle,
            "body" to cleanBody,
            "unlockAtMillis" to unlockAtMillis,
            "isSealed" to isSealed,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
          .filterNonNull()
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
        throw e
      }
    }
  }

  suspend fun deleteSharedReflection(coupleId: String, reflectionId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection("couples")
        .document(coupleId)
        .collection("shared_reflections")
        .document(reflectionId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  fun observeNotifications(coupleId: String): Flow<List<NotificationItemDto>> = flow {
    val uid = requireUserId()
    val path = "couples/$coupleId/notifications"
    emitAll(
      db.collection("couples")
        .document(coupleId)
        .collection("notifications")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              NotificationItemDto::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
        .catch { error ->
          val fsErr = error.unwrapFirestoreException()
          if (fsErr == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsErr ?: error, OperationType.LIST, path, auth)
          }
          throw error
        }
    )
  }

  suspend fun sendPartnerNotification(
    coupleId: String,
    memberUids: List<String>,
    recipientUid: String,
    kind: String,
    title: String,
    body: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(coupleId.isNotBlank() && recipientUid.isNotBlank() && memberUids.size == 2 && uid in memberUids) {
      "Data pasangan tidak valid untuk mengirim notifikasi."
    }
    val notificationId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val docRef =
      db.collection("couples")
        .document(coupleId)
        .collection("notifications")
        .document(notificationId)
    val cleanTitle = title.trim().ifBlank { "Kabar EVORIA" }.take(140)
    val cleanBody = body.trim().ifBlank { cleanTitle }.take(400)
    val payload =
      mapOf(
        "recipientUid" to recipientUid,
        "actorUid" to uid,
        "memberUids" to memberUids,
        "kind" to kind.trim().ifBlank { "info" }.take(40),
        "title" to cleanTitle,
        "body" to cleanBody,
        "isRead" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun markRemoteNotificationsRead(
    coupleId: String,
    notificationIds: List<String>,
  ): Result<Unit> = runCatching {
    requireUserId()
    if (notificationIds.isEmpty()) return@runCatching
    val col = db.collection("couples").document(coupleId).collection("notifications")
    for (id in notificationIds) {
      val docRef = col.document(id)
      try {
        docRef
          .update(
            mapOf(
              "isRead" to true,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        throw e
      }
    }
  }

  suspend fun savePrivateBackup(
    deviceLabel: String,
    albumCount: Long,
    memoryCount: Long,
    reflectionCount: Long,
    payloadJson: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val backupId = "bkp_${System.currentTimeMillis()}"
    val docRef =
      db.collection("users").document(uid).collection("private_backups").document(backupId)
    val payload =
      mapOf(
        "ownerUid" to uid,
        "deviceLabel" to deviceLabel.take(100),
        "albumCount" to albumCount.coerceAtLeast(0L),
        "memoryCount" to memoryCount.coerceAtLeast(0L),
        "reflectionCount" to reflectionCount.coerceAtLeast(0L),
        "payloadJson" to payloadJson.take(390_000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      backupId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun getLatestPrivateBackup(): Result<PrivateBackupDto?> = runCatching {
    val uid = requireUserId()
    val path = "users/$uid/private_backups"
    try {
      val snap =
        db.collection("users")
          .document(uid)
          .collection("private_backups")
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .limit(1)
          .get()
          .await()
      snap.documents.firstOrNull()?.toObject(
        PrivateBackupDto::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path, auth)
      throw e
    }
  }
}
