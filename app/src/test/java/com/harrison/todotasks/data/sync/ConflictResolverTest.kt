package com.harrison.todotasks.data.sync

import com.harrison.todotasks.data.model.TodoTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolverTest {

    private val resolver = ConflictResolver()

    private val base = TodoTask(
        id = "task-id",
        title = "Base title",
        description = "Base description",
        createdAt = 1_000L,
        lastUpdatedAt = 2_000L,
        deletedAt = null,
        dueAt = 5_000L,
        isComplete = false,
        version = 1L,
    )

    @Test
    fun `no changes on either side returns the base values`() {
        val result = resolver.resolve(base, local = base, remote = base)

        assertEquals(base, result)
    }

    @Test
    fun `field changed only locally takes the local value`() {
        val local = base.copy(title = "Local title", lastUpdatedAt = EARLIER)
        val remote = base.copy(lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Local title", result.title)
    }

    @Test
    fun `field changed only remotely takes the remote value`() {
        val local = base.copy(lastUpdatedAt = LATER)
        val remote = base.copy(title = "Remote title", lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Remote title", result.title)
    }

    @Test
    fun `every mergeable field takes the local value when changed only locally`() {
        val local = base.copy(
            title = "Local title",
            description = "Local description",
            dueAt = 6_000L,
            isComplete = true,
            lastUpdatedAt = EARLIER,
        )
        val remote = base.copy(lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Local title", result.title)
        assertEquals("Local description", result.description)
        assertEquals(6_000L, result.dueAt)
        assertTrue(result.isComplete)
    }

    @Test
    fun `every mergeable field takes the remote value when changed only remotely`() {
        val local = base.copy(lastUpdatedAt = LATER)
        val remote = base.copy(
            title = "Remote title",
            description = "Remote description",
            dueAt = 7_000L,
            isComplete = true,
            lastUpdatedAt = EARLIER,
        )

        val result = resolver.resolve(base, local, remote)

        assertEquals("Remote title", result.title)
        assertEquals("Remote description", result.description)
        assertEquals(7_000L, result.dueAt)
        assertTrue(result.isComplete)
    }

    @Test
    fun `same value set on both sides is kept`() {
        val local = base.copy(title = "Shared title", lastUpdatedAt = LATER)
        val remote = base.copy(title = "Shared title", lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Shared title", result.title)
    }

    @Test
    fun `both sides changed a field and later local wins`() {
        val local = base.copy(title = "Local title", lastUpdatedAt = LATER)
        val remote = base.copy(title = "Remote title", lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Local title", result.title)
    }

    @Test
    fun `both sides changed a field and later remote wins`() {
        val local = base.copy(title = "Local title", lastUpdatedAt = EARLIER)
        val remote = base.copy(title = "Remote title", lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Remote title", result.title)
    }

    @Test
    fun `timestamp tie goes to remote`() {
        val local = base.copy(title = "Local title", lastUpdatedAt = LATER)
        val remote = base.copy(title = "Remote title", lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Remote title", result.title)
    }

    @Test
    fun `every mergeable field in conflict takes the later side`() {
        val local = base.copy(
            title = "Local title",
            description = "Local description",
            dueAt = 6_000L,
            lastUpdatedAt = LATER,
        )
        val remote = base.copy(
            title = "Remote title",
            description = "Remote description",
            dueAt = 7_000L,
            lastUpdatedAt = EARLIER,
        )

        val result = resolver.resolve(base, local, remote)

        assertEquals("Local title", result.title)
        assertEquals("Local description", result.description)
        assertEquals(6_000L, result.dueAt)
    }

    @Test
    fun `different fields changed on each side both survive`() {
        val local = base.copy(title = "Local title", lastUpdatedAt = EARLIER)
        val remote = base.copy(isComplete = true, lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Local title", result.title)
        assertTrue(result.isComplete)
    }

    @Test
    fun `timestamp comparison applies only to the conflicting field`() {
        val local = base.copy(
            title = "Local title",
            description = "Local description",
            lastUpdatedAt = EARLIER,
        )
        val remote = base.copy(title = "Remote title", lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Remote title", result.title)
        assertEquals("Local description", result.description)
    }

    @Test
    fun `dueAt set locally from null takes the local value`() {
        val noDueDate = base.copy(dueAt = null)
        val local = noDueDate.copy(dueAt = 6_000L)

        val result = resolver.resolve(noDueDate, local, remote = noDueDate)

        assertEquals(6_000L, result.dueAt)
    }

    @Test
    fun `dueAt set remotely from null takes the remote value`() {
        val noDueDate = base.copy(dueAt = null)
        val remote = noDueDate.copy(dueAt = 6_000L)

        val result = resolver.resolve(noDueDate, local = noDueDate, remote)

        assertEquals(6_000L, result.dueAt)
    }

    @Test
    fun `dueAt cleared locally is not restored by an unchanged remote`() {
        val local = base.copy(dueAt = null)

        val result = resolver.resolve(base, local, remote = base)

        assertNull(result.dueAt)
    }

    @Test
    fun `dueAt cleared remotely is not restored by an unchanged local`() {
        val remote = base.copy(dueAt = null)

        val result = resolver.resolve(base, local = base, remote)

        assertNull(result.dueAt)
    }

    @Test
    fun `dueAt cleared on both sides stays cleared`() {
        val local = base.copy(dueAt = null, lastUpdatedAt = LATER)
        val remote = base.copy(dueAt = null, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertNull(result.dueAt)
    }

    @Test
    fun `later local clear beats remote dueAt edit`() {
        val local = base.copy(dueAt = null, lastUpdatedAt = LATER)
        val remote = base.copy(dueAt = 7_000L, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertNull(result.dueAt)
    }

    @Test
    fun `later remote edit beats local dueAt clear`() {
        val local = base.copy(dueAt = null, lastUpdatedAt = EARLIER)
        val remote = base.copy(dueAt = 7_000L, lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(7_000L, result.dueAt)
    }

    @Test
    fun `later remote clear beats local dueAt edit`() {
        val local = base.copy(dueAt = 6_000L, lastUpdatedAt = EARLIER)
        val remote = base.copy(dueAt = null, lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertNull(result.dueAt)
    }

    @Test
    fun `later local edit beats remote dueAt clear`() {
        val local = base.copy(dueAt = 6_000L, lastUpdatedAt = LATER)
        val remote = base.copy(dueAt = null, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(6_000L, result.dueAt)
    }

    @Test
    fun `local delete wins over a later remote edit`() {
        val local = base.copy(deletedAt = 3_000L, lastUpdatedAt = EARLIER)
        val remote = base.copy(title = "Remote title", lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(3_000L, result.deletedAt)
    }

    @Test
    fun `remote delete wins over a later local edit`() {
        val local = base.copy(title = "Local title", lastUpdatedAt = LATER)
        val remote = base.copy(deletedAt = 3_000L, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(3_000L, result.deletedAt)
    }

    @Test
    fun `delete on both sides keeps the remote tombstone`() {
        val local = base.copy(deletedAt = 4_000L, lastUpdatedAt = LATER)
        val remote = base.copy(deletedAt = 3_000L, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(3_000L, result.deletedAt)
    }

    @Test
    fun `tombstone still merges the other fields`() {
        val local = base.copy(title = "Local title", lastUpdatedAt = LATER)
        val remote = base.copy(deletedAt = 3_000L, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("Local title", result.title)
    }

    @Test
    fun `no tombstone on either side stays undeleted`() {
        val local = base.copy(title = "Local title")
        val remote = base.copy(description = "Remote description")

        val result = resolver.resolve(base, local, remote)

        assertNull(result.deletedAt)
    }

    @Test
    fun `version always comes from remote`() {
        val local = base.copy(version = 1L, lastUpdatedAt = LATER)
        val remote = base.copy(version = 2L, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(2L, result.version)
    }

    @Test
    fun `version from remote replaces a null local version`() {
        val neverSynced = base.copy(version = null)
        val remote = neverSynced.copy(version = 2L)

        val result = resolver.resolve(neverSynced, local = neverSynced, remote)

        assertEquals(2L, result.version)
    }

    @Test
    fun `lastUpdatedAt is the later value when local is later`() {
        val local = base.copy(lastUpdatedAt = LATER)
        val remote = base.copy(lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(LATER, result.lastUpdatedAt)
    }

    @Test
    fun `lastUpdatedAt is the later value when remote is later`() {
        val local = base.copy(lastUpdatedAt = EARLIER)
        val remote = base.copy(lastUpdatedAt = LATER)

        val result = resolver.resolve(base, local, remote)

        assertEquals(LATER, result.lastUpdatedAt)
    }

    @Test
    fun `identity fields come from remote`() {
        val local = base.copy(id = "local-id", createdAt = 1_100L, lastUpdatedAt = LATER)
        val remote = base.copy(id = "remote-id", createdAt = 1_200L, lastUpdatedAt = EARLIER)

        val result = resolver.resolve(base, local, remote)

        assertEquals("remote-id", result.id)
        assertEquals(1_200L, result.createdAt)
    }

    private companion object {
        const val EARLIER = 3_000L
        const val LATER = 4_000L
    }
}
