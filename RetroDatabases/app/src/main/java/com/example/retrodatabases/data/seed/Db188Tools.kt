package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 160 to 170: a Roman clay spindle whorl, undated in the sources and placed at 161, the year Marcus Aurelius and Lucius Verus came to share the throne. */
val db188Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.TOOL,
        section = "Spinning",
        title = "The Roman clay spindle whorl, a weight that steadied the thread",
        originalTitle = "Spindle whorl",
        author = "No single author",
        year = 161,
        summary = "A clay spindle whorl was a weight on the spindle that kept it turning while thread was drawn, and it is placed at 161 for the joint accession of Marcus Aurelius and Lucius Verus.",
        details = "The sources do not date this tool; 161 is the year of the anchor, the joint accession of Marcus Aurelius and Lucius Verus, not of the whorl's making. Whorls of baked clay are common finds across the Roman provinces, and the placed year says nothing about when the first one was shaped.",
        rare = true,
    ),

)
