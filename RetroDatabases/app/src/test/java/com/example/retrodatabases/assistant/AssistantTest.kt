package com.example.retrodatabases.assistant

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.seed.seedDatabases
import com.example.retrodatabases.data.seed.seedRecords
import com.example.retrodatabases.data.seed.seedYears
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The assistant on the real catalogue: the answers must match the seed data. */
class AssistantTest {

    private val assistant = Assistant(seedDatabases, seedRecords, seedYears)

    @Test
    fun countsTheCatalogue() {
        val answer = assistant.answer("how many records are there")
        assertEquals(Intent.STATS, answer.intent)
        assertTrue(answer.text, answer.text.contains("${seedRecords.size} records"))
    }

    @Test
    fun listsTheDatabases() {
        val answer = assistant.answer("list the databases")
        assertEquals(Intent.DATABASES, answer.intent)
        assertTrue(answer.text, answer.text.contains("There are ${seedDatabases.size} databases"))
    }

    @Test
    fun namesTheDatabasesThatCoverAYear() {
        val year = 1850
        val expected = seedYears.filterValues { year in it }.keys.sorted()
        assertTrue("some database should cover $year", expected.isNotEmpty())
        val answer = assistant.answer("which database covers the year $year")
        assertEquals(Intent.COVERAGE, answer.intent)
        assertTrue(answer.text, answer.text.contains(expected.joinToString(", ")))
    }

    @Test
    fun describesOneDatabase() {
        val answer = assistant.answer("what years does database 12 cover")
        assertEquals(Intent.COVERAGE, answer.intent)
        assertTrue(answer.text, answer.text.startsWith("Database 12 ("))
    }

    @Test
    fun offersOnlyRunnableRecords() {
        val answer = assistant.answer("show me runnable examples")
        assertEquals(Intent.RUNNABLE, answer.intent)
        assertTrue(answer.hits.isNotEmpty())
        val runnable = setOf(Kind.GUIDE, Kind.EXAMPLE, Kind.PROJECT, Kind.FLEX, Kind.CROSS)
        assertTrue(answer.hits.all { it.record.kind in runnable && it.record.example != null })
    }

    @Test
    fun explainsTheAppInEnglish() {
        val answer = assistant.answer("how do I use the app")
        assertEquals(Intent.APP_HELP, answer.intent)
        assertTrue(answer.text, answer.text.contains("first screen"))
    }

    @Test
    fun findsRecordsByTitle() {
        val answer = assistant.answer("find Liu Hui")
        assertEquals(Intent.FIND, answer.intent)
        assertTrue(answer.hits.isNotEmpty())
        assertTrue(answer.hits.first().record.title.contains("Liu Hui"))
    }

    @Test
    fun answersRussianQuestionsInRussian() {
        val answer = assistant.answer("сколько всего записей")
        assertEquals(Intent.STATS, answer.intent)
        assertTrue(answer.text, answer.text.contains("записей"))
    }

    @Test
    fun restrictsASearchToTheDatabaseNamed() {
        val answer = assistant.answer("find commentary in database 178")
        assertEquals(Intent.FIND, answer.intent)
        assertTrue(answer.hits.isNotEmpty())
        assertTrue(answer.hits.all { it.record.databaseId == 178L })
    }
}
