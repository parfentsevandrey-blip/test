package com.example.retrodatabases.assistant

import com.example.retrodatabases.data.seed.seedRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordIndexTest {

    private val index by lazy { RecordIndex(seedRecords) }

    @Test
    fun findsRecordsByTheirTitleWords() {
        val top = index.search("Liu Hui", limit = 5)
        assertTrue(top.isNotEmpty())
        assertTrue(top.first().record.title.contains("Liu Hui"))
    }

    @Test
    fun unknownWordsGiveNoHits() {
        assertTrue(index.search("zzqxw plugh", limit = 5).isEmpty())
    }

    @Test
    fun hitsAreSortedBestFirst() {
        val hits = index.search("commentary", limit = 50)
        assertTrue(hits.size > 1)
        assertEquals(hits.map { it.score }.sortedDescending(), hits.map { it.score })
    }

    @Test
    fun indexCoversTheWholeCatalogue() {
        assertTrue(index.termCount > 1000)
        assertTrue(index.postingCount > index.termCount)
    }
}
