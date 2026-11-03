package com.harrison.todotasks.data.local

import com.harrison.todotasks.data.local.dao.SyncStateDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncStateDaoTest {

    private lateinit var todoTaskDatabase: TodoTaskDatabase
    private lateinit var dao: SyncStateDao

    @Before
    fun setUp() {
        todoTaskDatabase = inMemoryTodoTaskDatabase()
        dao = todoTaskDatabase.syncStateDao()
    }

    @After
    fun tearDown() {
        todoTaskDatabase.close()
    }

    @Test
    fun `cursor defaults to zero when there is no row`() = runTest {
        assertEquals(0L, dao.getCursor())
    }

    @Test
    fun `cursor returns the stored value`() = runTest {
        dao.setCursor(42L)

        assertEquals(42L, dao.getCursor())
    }

    @Test
    fun `setting the cursor again replaces the single row`() = runTest {
        dao.setCursor(42L)

        dao.setCursor(43L)

        assertEquals(43L, dao.getCursor())
    }
}
