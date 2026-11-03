package com.harrison.todotasks.data.local

import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.local.entities.asTodoTask
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoTaskMappersTest {

    @Test
    fun `round trip keeps every field`() {
        assertEquals(fullTask, fullTask.asEntity().asTodoTask())
    }

    @Test
    fun `round trip keeps null fields`() {
        assertEquals(sparseTask, sparseTask.asEntity().asTodoTask())
    }

    @Test
    fun `asEntity maps each field to its own property`() {
        val entity = fullTask.asEntity()

        assertEquals(fullTask.id, entity.id)
        assertEquals(fullTask.title, entity.title)
        assertEquals(fullTask.description, entity.description)
        assertEquals(fullTask.createdAt, entity.createdAt)
        assertEquals(fullTask.lastUpdatedAt, entity.lastUpdatedAt)
        assertEquals(fullTask.deletedAt, entity.deletedAt)
        assertEquals(fullTask.dueAt, entity.dueAt)
        assertEquals(fullTask.parentTaskId, entity.parentTaskId)
        assertEquals(fullTask.isComplete, entity.isComplete)
        assertEquals(fullTask.version, entity.version)
    }
}
