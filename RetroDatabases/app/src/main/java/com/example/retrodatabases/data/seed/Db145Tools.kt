package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 590 to 600: Maurice's Strategikon, the Byzantine army handbook dated about 592, and Fei Changfang's catalogue of Buddhist scriptures of 597. */
val db145Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.TOOL,
        section = "Byzantine",
        title = "Maurice's Strategikon, a Greek manual of army campaigns in twelve books, dated about 592",
        originalTitle = "Strategikon",
        author = "Attributed to Emperor Maurice",
        year = 592,
        summary = "The Strategikon is a Greek manual of army organization, campaigns, sieges and supply in twelve books, linked with Emperor Maurice and dated about 592.",
        details = "George Dennis puts its composition at about 592, while other scholars date it to the early seventh century, and internal references allow a window from 585 to 628. The emperor may have ordered the work rather than written it, and his brother Peter and his brother-in-law Philippicus are among the names put forward for its author.",
    ),

    entry(
        databaseId = 145,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "Fei Changfang's Lidai sanbao ji, a catalogue of Buddhist scriptures arranged by dynasty, compiled in 597",
        originalTitle = "Lidai sanbao ji",
        author = "Fei Changfang",
        year = 597,
        summary = "Fei Changfang's Lidai sanbao ji is a Chinese catalogue of Buddhist scriptures arranged by the successive dynasties, compiled under the early Sui.",
        details = "Accounts give its compilation as 597, while a 2019 study gives 598, so 597 is used here. Most modern scholars judge Fei an unreliable bibliographer, yet its translator attributions for early scriptures were widely copied and still appear in modern canon editions.",
        rare = true,
    ),

)
