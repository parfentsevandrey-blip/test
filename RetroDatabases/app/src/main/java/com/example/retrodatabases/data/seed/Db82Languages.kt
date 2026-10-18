package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1220 to 1230: a Mongol chronicle, the Ancrene Wisse, a Chan collection and Ibn Arabi's Bezels of Wisdom. */
val db82Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.LANGUAGE,
        section = "Mongol",
        title = "The Secret History of the Mongols, a Mongol chronicle of about 1228",
        originalTitle = "Mongghol-un ni'uca tobca'an",
        author = "An anonymous Mongol author",
        year = 1228,
        summary = "The Secret History of the Mongols is the oldest surviving book in the Mongol language, a chronicle of Genghis Khan and his family written in the Mongol script.",
        details = "It is usually dated to 1228, a year of the Rat, although some scholars argue for 1240, another Rat year, so accounts differ. The Mongol original is lost, and the text survives in a Chinese transcription of the Mongol words, made later in the Ming period.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "The Ancrene Wisse, a rule of life for three women who lived as anchoresses",
        originalTitle = "Ancrene Wisse",
        author = "An anonymous author, probably a priest",
        year = 1225,
        summary = "The Ancrene Wisse is an English prose guide for women who had withdrawn from the world to live as anchoresses, covering their prayers, confession and daily conduct.",
        details = "The usual date is about 1225, with editors citing a span from about 1215 to 1230, and the place of writing is disputed. It is one of the earliest substantial prose works in English, and its surviving copies date from the thirteenth century.",
        rare = true,
    ),

    entry(
        databaseId = 82,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Gateless Gate, forty-eight Chan cases compiled by Wumen Huikai in 1228",
        originalTitle = "Wumenguan",
        author = "Wumen Huikai",
        year = 1228,
        summary = "The Gateless Gate is a Chan Buddhist collection of forty-eight cases, short exchanges between masters and students, each followed by a commentary and a verse.",
        details = "Wumen Huikai, a Chan master of the Southern Song, compiled it in 1228 in the south of China, and the book later became a standard Zen text in Japan. Its cases are meant for meditation, and the commentary and verse are Wumen's own.",
        rare = true,
    ),

    entry(
        databaseId = 82,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Ibn Arabi's Bezels of Wisdom, twenty-seven chapters on the prophets",
        originalTitle = "Fusus al-hikam",
        author = "Ibn Arabi",
        year = 1229,
        summary = "Ibn Arabi wrote the Bezels of Wisdom in Damascus in 1229, a mystical book of twenty-seven chapters, each named after a prophet and each on the wisdom that the prophet carries.",
        details = "Ibn Arabi said that the Prophet gave him the book in a vision, and its date, 627 of the Islamic calendar, falls in 1229 and 1230 across the two reckonings. The book became one of the most commented Sufi texts, and many later writers wrote commentaries on it.",
        rare = true,
    ),

)
