package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileDto(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val partnerNickname: String? = null,
  val avatarUrl: String? = null,
  val coupleId: String? = null,
  val partnerUid: String? = null,
  val partnerDisplayName: String? = null,
  val aiSearchConsent: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class InvitationDto(
  @DocumentId val id: String = "",
  val codeHash: String = "",
  val inviterUid: String = "",
  val inviterName: String = "",
  val recipientUid: String? = null,
  val recipientName: String? = null,
  val coupleId: String? = null,
  val status: String = "pending",
  val expiresAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CoupleConnectionDto(
  @DocumentId val id: String = "",
  val memberUids: List<String> = emptyList(),
  val memberOneUid: String = "",
  val memberTwoUid: String = "",
  val memberOneName: String = "",
  val memberTwoName: String = "",
  val anniversaryLabel: String? = null,
  val status: String = "active",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SharedAlbumDto(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val memberUids: List<String> = emptyList(),
  val title: String = "",
  val description: String? = null,
  val soundtrackTitle: String? = null,
  val itemCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SharedMemoryDto(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val memberUids: List<String> = emptyList(),
  val albumId: String? = null,
  val title: String = "",
  val caption: String? = null,
  val story: String? = null,
  val locationName: String? = null,
  val mediaType: String = "photo",
  val memoryDateMillis: Long = 0L,
  val isFavorite: Boolean = false,
  val tags: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SharedContentRequestDto(
  @DocumentId val id: String = "",
  val senderUid: String = "",
  val senderName: String = "",
  val recipientUid: String = "",
  val memberUids: List<String> = emptyList(),
  val memoryId: String? = null,
  val albumId: String? = null,
  val title: String = "",
  val caption: String? = null,
  val status: String = "pending",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@kotlinx.serialization.Serializable
data class SharedMusicTrackItem(
  val title: String,
  val artist: String = "",
  val durationMs: Long = 0L,
  val sourceType: String = "spotify",
  val externalUrl: String? = null,
  val coverUrl: String? = null,
  val youtubeVideoId: String? = null,
)

@IgnoreExtraProperties
data class SharedMusicDto(
  @DocumentId val id: String = "",
  val senderUid: String = "",
  val senderName: String = "",
  val recipientUid: String = "",
  val memberUids: List<String> = emptyList(),
  val kind: String = "track", // "track" | "playlist"
  val title: String = "",
  val subtitle: String? = null,
  val externalUrl: String? = null,
  val coverUrl: String? = null,
  val tracksJson: String = "[]",
  val status: String = "pending", // "pending" | "accepted" | "declined" | "revoked"
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SharedReflectionDto(
  @DocumentId val id: String = "",
  val authorUid: String = "",
  val authorName: String = "",
  val memberUids: List<String> = emptyList(),
  val kind: String = "journal",
  val title: String = "",
  val body: String = "",
  val unlockAtMillis: Long? = null,
  val isSealed: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class NotificationItemDto(
  @DocumentId val id: String = "",
  val recipientUid: String = "",
  val actorUid: String = "",
  val memberUids: List<String> = emptyList(),
  val kind: String = "info",
  val title: String = "",
  val body: String = "",
  val isRead: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PrivateBackupDto(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val deviceLabel: String = "",
  val albumCount: Long = 0L,
  val memoryCount: Long = 0L,
  val reflectionCount: Long = 0L,
  val payloadJson: String = "{}",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

internal fun Map<String, Any?>.filterNonNull(): Map<String, Any> {
  val result = LinkedHashMap<String, Any>()
  for ((k, v) in this) {
    if (v != null) result[k] = v
  }
  return result
}

internal fun serverTimestamp(): Any = FieldValue.serverTimestamp()
