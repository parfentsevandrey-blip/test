package com.example.retrodatabases.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentClassifierTest {

    private val classifier = IntentClassifier()

    private fun intentOf(text: String): Intent = classifier.classify(text).first

    @Test
    fun recognisesEnglishQuestions() {
        assertEquals(Intent.DATABASES, intentOf("list all databases please"))
        assertEquals(Intent.COVERAGE, intentOf("which database covers the year 1812"))
        assertEquals(Intent.RUNNABLE, intentOf("show me runnable programs"))
        assertEquals(Intent.APP_HELP, intentOf("how do I search in the app"))
        assertEquals(Intent.STATS, intentOf("how many rare records are there"))
        assertEquals(Intent.FIND, intentOf("find the record on the abacus"))
    }

    @Test
    fun recognisesRussianQuestions() {
        assertEquals(Intent.DATABASES, intentOf("какие базы есть"))
        assertEquals(Intent.APP_HELP, intentOf("как искать в приложении"))
        assertEquals(Intent.STATS, intentOf("сколько всего записей"))
        assertEquals(Intent.COVERAGE, intentOf("какая база покрывает 1850 год"))
    }

    @Test
    fun wordsNeverSeenGiveUnknownWithNoConfidence() {
        val (intent, confidence) = classifier.classify("xyzzy plugh")
        assertEquals(Intent.UNKNOWN, intent)
        assertEquals(0.0, confidence, 0.0)
    }

    @Test
    fun confidenceIsAProbability() {
        val (_, confidence) = classifier.classify("how many records")
        assertTrue(confidence > 0.0 && confidence <= 1.0)
    }
}
