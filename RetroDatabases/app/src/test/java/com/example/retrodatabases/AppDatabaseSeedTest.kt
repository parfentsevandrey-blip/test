package com.example.retrodatabases

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.retrodatabases.data.AppDatabase
import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.db1Records
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the real database: schema validation, the seed SQL in onCreate and the favourite toggle. */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseSeedTest {

    @Test
    fun firstOpenSeedsDatabaseOneWithEveryRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.get(context).libraryDao()

        val databases = dao.observeDatabases().first()
        assertEquals(1, databases.size)
        assertEquals(db1Records.size, databases.single().recordCount)

        val records = dao.observeRecords(1L).first()
        assertEquals(db1Records.size, records.size)
        assertTrue(records.any { it.kind == Kind.PROJECT && it.instructions != null })
        assertTrue(records.any { it.rare })
    }

    @Test
    fun toggleFavoriteStoresTheChange() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.get(context).libraryDao()

        val record = dao.observeRecords(1L).first().first()
        assertEquals(false, record.favorite)

        dao.toggleFavorite(record.id)
        val updated = dao.observeRecord(record.id).first()
        assertNotNull(updated)
        assertEquals(true, updated!!.favorite)
    }
}
