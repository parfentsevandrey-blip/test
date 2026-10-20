package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 180 to 190: a placed record for ostraca, potsherds written on in ink in Roman Egypt, placed at 180 by the death of Marcus Aurelius. */
val db186Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.TOOL,
        section = "Writing",
        title = "Ostraca, potsherds written on in ink in Roman Egypt",
        originalTitle = "Ostraka",
        author = "Clerks and pupils of Roman Egypt",
        year = 180,
        summary = "Broken pottery served as cheap writing material for receipts, tax slips and school exercises, undated in the sources and placed at 180.",
        details = "The sources do not date the use of potsherds for writing, so 180 is the year of the anchor, the death of Marcus Aurelius, not the year of any ostracon. This is a placed record, and it rests only on the anchor.",
        rare = true,
    ),

)
