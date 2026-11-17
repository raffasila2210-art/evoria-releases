package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvoriaFirestoreRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun userProfile_upsertAndObserve_succeedsForOwner() = runBlocking {
    val uid = signInTestUser("nadia_${UUID.randomUUID()}@evoria.test")
    val repo = EvoriaCloudRepository(firestore, auth)

    val upsert = withTimeout(TIMEOUT_MS) { repo.upsertUserProfile("Nadia", "Sayang") }
    assertTrue("Expected upsert success, got ${upsert.exceptionOrNull()}", upsert.isSuccess)

    val profile = withTimeout(TIMEOUT_MS) { repo.observeUserProfile(uid).first { it != null } }
    assertNotNull(profile)
    assertEquals("Nadia", profile?.displayName)
  }

  @Test
  fun userProfile_concurrentUpsert_doesNotTriggerPermissionDeniedRace() = runBlocking {
    val uid = signInTestUser("raffa_${UUID.randomUUID()}@evoria.test")
    val repo = EvoriaCloudRepository(firestore, auth)

    val results =
      withTimeout(TIMEOUT_MS) {
        listOf(
            async { repo.upsertUserProfile("Raffa", "Sayang") },
            async { repo.upsertUserProfile("Raffa", "") },
          )
          .awaitAll()
      }
    results.forEachIndexed { idx, res ->
      assertTrue("Call $idx failed: ${res.exceptionOrNull()}", res.isSuccess)
    }

    val profile = withTimeout(TIMEOUT_MS) { repo.observeUserProfile(uid).first { it != null } }
    assertNotNull(profile)
    assertEquals("Raffa", profile?.displayName)
  }

  @Test
  fun invitation_createLookupAccept_connectsTwoPartnersAndBlocksSelfLink() = runBlocking {
    val aliceEmail = "alice_${UUID.randomUUID()}@evoria.test"
    val bobEmail = "bob_${UUID.randomUUID()}@evoria.test"

    signInTestUser(aliceEmail)
    val aliceRepo = EvoriaCloudRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) { aliceRepo.upsertUserProfile("Nadia") }.getOrThrow()

    val generated = withTimeout(TIMEOUT_MS) { aliceRepo.createInvitation("Nadia").getOrThrow() }
    assertEquals(6, generated.rawCode.length)

    // Self-lookup must fail
    val selfLookup = withTimeout(TIMEOUT_MS) { aliceRepo.lookupInvitation(generated.rawCode) }
    assertTrue(selfLookup.isFailure)

    // Partner Bob looks up and accepts
    val bobUid = signInTestUser(bobEmail)
    val bobRepo = EvoriaCloudRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) { bobRepo.upsertUserProfile("Raffa") }.getOrThrow()

    val found = withTimeout(TIMEOUT_MS) { bobRepo.lookupInvitation(generated.rawCode).getOrThrow() }
    assertEquals("Nadia", found.inviterName)

    val couple = withTimeout(TIMEOUT_MS) { bobRepo.acceptInvitation(found.codeHash, "Raffa").getOrThrow() }
    assertTrue(couple.memberUids.contains(bobUid))
    assertEquals("active", couple.status)

    // Re-using same code must fail
    val reuse = withTimeout(TIMEOUT_MS) { bobRepo.lookupInvitation(generated.rawCode) }
    assertTrue(reuse.isFailure)
  }

  @Test
  fun sharedMemories_unauthenticatedObserver_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = EvoriaCloudRepository(firestore, auth)
    var caught: Throwable? = null
    try {
      withTimeout(TIMEOUT_MS) { repo.observeUserProfile("any_user").first() }
    } catch (t: Throwable) {
      caught = t
    }
    val fsErr = caught?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException, got $caught", fsErr)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
  }

  private companion object {
    const val TIMEOUT_MS = 8000L
  }
}
