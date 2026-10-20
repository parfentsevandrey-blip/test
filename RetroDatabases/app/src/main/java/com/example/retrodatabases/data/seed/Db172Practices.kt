package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practice of 320: Pachomius's common life of monks at Tabennisi, with shared prayer and work under one rule. */
val db172Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.PRACTICE,
        section = "Monks",
        title = "Pachomius's common life of monks at Tabennisi, with shared prayer and work under one rule",
        originalTitle = "Pachomian common life",
        author = "Pachomius and his monks",
        year = 320,
        summary = "Pachomius founded at Tabennisi in Egypt the first community of monks who lived a common life under one written rule, a model that spread along the Nile.",
        details = "Accounts date the first house to between 318 and 323, and this record uses 320, the middle of that span. The rule survives in Coptic and Greek versions whose dates are disputed, so the year refers only to the founding.",
        rare = true,
    ),

)
