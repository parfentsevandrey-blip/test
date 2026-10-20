package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 0 to 10: one placed practice, the Han frontier beacon signals of smoke by day and fire by night, placed at 1 for the start of the Yuanshi era. */
val db204Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.PRACTICE,
        section = "Signals",
        title = "Smoke by day and fire by night, the frontier beacon signals of the Han, undated in the sources and placed at 1",
        originalTitle = "Fenghuo, the beacon fire",
        author = "Han frontier garrisons",
        year = 1,
        summary = "Han frontier posts passed a warning along their line by smoke in daylight and fire at night, and the record is placed at 1, the first year of the Yuanshi era, not the date of the practice.",
        details = "The sources do not date the signal rules as one code, so the record uses 1, the first year of the Yuanshi era under Emperor Ping, as its anchor and not as the date of the practice. Because each signal was agreed in advance to mean one thing, the line of posts worked as an early code for messages.",
        rare = true,
    ),

)
