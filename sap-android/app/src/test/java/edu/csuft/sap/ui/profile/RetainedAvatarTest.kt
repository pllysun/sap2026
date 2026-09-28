package edu.csuft.sap.ui.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RetainedAvatarTest {
    private class Picture(val pixels: String)
    private fun cache() = RetainedAvatar<Picture> { a, b -> a.pixels == b.pixels }

    @Test
    fun returningToTheSameAvatarKeepsTheDecodedImageWithoutLoadingAgain() = runBlocking {
        val cache = cache()
        val old = Picture("old")
        cache.load("alice", "avatar?v=1") { old }

        repeat(5) { cache.load("alice", "avatar?v=1") { error("unchanged avatar must not reload") } }

        assertSame(old, cache.image.value)
    }

    @Test
    fun slowNewAvatarKeepsTheOldImageUntilSuccess() = runBlocking {
        val cache = cache()
        val old = Picture("old")
        val next = Picture("new")
        cache.load("alice", "v1") { old }
        val download = CompletableDeferred<Picture>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v2") { download.await() } }

        assertSame(old, cache.image.value)
        download.complete(next)
        job.join()
        assertSame(next, cache.image.value)
    }

    @Test
    fun newProfileRevisionWithIdenticalPixelsDoesNotReplaceTheVisibleImage() = runBlocking {
        val cache = cache()
        val original = Picture("same pixels")
        cache.load("alice", "v1") { original }
        cache.load("alice", "v2") { Picture("same pixels") }

        assertSame(original, cache.image.value)
        cache.load("alice", "v2") { error("validated revision must be cached") }
    }

    @Test
    fun failedDownloadPreservesOldImageAndAllowsRetry() = runBlocking {
        val cache = cache()
        val old = Picture("old")
        val next = Picture("new")
        cache.load("alice", "v1") { old }
        cache.load("alice", "v2") { null }
        assertSame(old, cache.image.value)

        cache.load("alice", "v2") { next }
        assertSame(next, cache.image.value)
    }

    @Test
    fun duplicatePendingRequestsShareTheExistingLoad() = runBlocking {
        val cache = cache()
        val download = CompletableDeferred<Picture>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v1") { download.await() } }
        cache.load("alice", "v1") { error("duplicate download") }
        download.complete(Picture("one download"))
        job.join()
        assertEquals("one download", cache.image.value?.pixels)
    }

    @Test
    fun olderDownloadCannotOverwriteTheNewestAvatar() = runBlocking {
        val cache = cache()
        val slow = CompletableDeferred<Picture>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v1") { slow.await() } }
        val newest = Picture("newest")
        cache.load("alice", "v2") { newest }
        slow.complete(Picture("outdated"))
        job.join()
        assertSame(newest, cache.image.value)
    }

    @Test
    fun accountSwitchClearsThePreviousUsersImageAndRejectsLateResponses() = runBlocking {
        val cache = cache()
        cache.load("alice", "v1") { Picture("alice") }
        val slow = CompletableDeferred<Picture>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v2") { slow.await() } }
        cache.activate("bob")
        assertNull(cache.image.value)
        val bob = Picture("bob")
        cache.load("bob", "v1") { bob }
        slow.complete(Picture("alice-new"))
        job.join()
        assertSame(bob, cache.image.value)
    }

    @Test
    fun removingAvatarAlsoInvalidatesPendingDownload() = runBlocking {
        val cache = cache()
        cache.load("alice", "v1") { Picture("old") }
        val slow = CompletableDeferred<Picture>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v2") { slow.await() } }
        cache.load("alice", null) { error("removed avatar must not load") }
        slow.complete(Picture("removed"))
        job.join()
        assertNull(cache.image.value)
    }

    @Test
    fun returningToTheLoadedVersionInvalidatesANewerPendingRequest() = runBlocking {
        val cache = cache()
        val original = Picture("original")
        cache.load("alice", "v1") { original }
        val slow = CompletableDeferred<Picture>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v2") { slow.await() } }
        cache.load("alice", "v1") { error("already loaded") }
        slow.complete(Picture("obsolete"))
        job.join()
        assertSame(original, cache.image.value)
    }

    @Test
    fun cancelledDownloadCanBeRetried() = runBlocking {
        val cache = cache()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            cache.load("alice", "v1") { CompletableDeferred<Picture>().await() }
        }
        job.cancelAndJoin()
        val retry = Picture("retry")
        cache.load("alice", "v1") { retry }
        assertSame(retry, cache.image.value)
    }

    @Test
    fun coldStartCanShowTheOldCacheWhileTheNewVersionIsStillLoading() = runBlocking {
        val cache = cache()
        val old = Picture("old")
        val next = Picture("new")
        val oldDecode = CompletableDeferred<Picture>()
        val newDownload = CompletableDeferred<Picture>()
        val restore = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v1") { oldDecode.await() } }
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) { cache.load("alice", "v2") { newDownload.await() } }
        oldDecode.complete(old)
        restore.join()
        assertSame(old, cache.image.value)
        newDownload.complete(next)
        refresh.join()
        assertSame(next, cache.image.value)
    }
}
