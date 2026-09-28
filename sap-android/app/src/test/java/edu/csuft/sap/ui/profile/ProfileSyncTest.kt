package edu.csuft.sap.ui.profile

import edu.csuft.sap.data.remote.dto.MeData
import edu.csuft.sap.data.remote.dto.UserDto
import edu.csuft.sap.data.repository.mergeProfileWithoutAvatar
import edu.csuft.sap.data.repository.needsFullProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSyncTest {
    private val cached = MeData(user = UserDto(id = 1, avatar = "https://example.com/avatar.jpg"), updatedAt = 100L)

    @Test
    fun unchangedProfileKeepsAvatarAndDoesNotFetchTheFullProfile() {
        val light = cached.copy(user = cached.user?.copy(avatar = null))
        assertFalse(needsFullProfile(cached, light))
        assertEquals(cached, mergeProfileWithoutAvatar(cached, light))
    }

    @Test
    fun knownEmptyAvatarDoesNotCauseRepeatedFullRequests() {
        val empty = cached.copy(user = cached.user?.copy(avatar = null))
        assertFalse(needsFullProfile(empty, empty))
    }

    @Test
    fun changedProfileRequiresTheFullAvatarInformation() {
        assertTrue(needsFullProfile(cached, cached.copy(updatedAt = 200L)))
    }

    @Test
    fun failedFullRequestKeepsTheOldAvatarVersionSoNextVisitRetries() {
        val light = cached.copy(user = UserDto(id = 1, nickname = "新网名"), updatedAt = 200L)
        val fallback = mergeProfileWithoutAvatar(cached, light)

        assertEquals("新网名", fallback.user?.nickname)
        assertEquals(cached.user?.avatar, fallback.user?.avatar)
        assertEquals(100L, fallback.updatedAt)
        assertTrue(needsFullProfile(fallback, light))
    }

    @Test
    fun missingCacheNeverConfirmsAnAvatarFromTheLightEndpoint() {
        val light = MeData(user = UserDto(id = 1), updatedAt = 200L)
        val fallback = mergeProfileWithoutAvatar(null, light)
        assertNull(fallback.updatedAt)
        assertTrue(needsFullProfile(fallback, light))
    }
}
