package com.example.retrodatabases.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenizerTest {

    @Test
    fun splitsOnPunctuationAndLowercases() {
        assertEquals(listOf("liu", "hui", "s", "commentary"), Tokenizer.tokens("Liu Hui's Commentary!"))
    }

    @Test
    fun keepsRussianWordsAndFoldsTheLetterYo() {
        assertEquals(listOf("елка", "база", "1850"), Tokenizer.tokens("Ёлка, база 1850"))
    }

    @Test
    fun contentTokensDropRequestWordsAndSingleLetters() {
        assertEquals(listOf("liu", "hui"), Tokenizer.contentTokens("find the Liu Hui and a"))
    }
}
