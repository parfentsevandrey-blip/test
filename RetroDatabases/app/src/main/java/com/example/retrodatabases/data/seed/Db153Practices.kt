package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 510 to 520: dating a year by its consuls, a placed record whose year is the consulship of Boethius in 510. */
val db153Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.PRACTICE,
        section = "Roman",
        title = "Dating years by their consuls, the Roman custom that charters of the early sixth century still followed",
        originalTitle = "post consulatum",
        author = "Roman and royal chanceries",
        year = 510,
        summary = "A year could take its name from its two consuls, a Roman dating custom that charters of the early sixth century still followed.",
        details = "The sources do not date the start of consular dating, which goes back to the Roman Republic, so 510 is the year of an anchor, the consulship of Boethius, and not the date of the custom. The custom lasted until the consulship itself ended in 541, and the record sits at 510 only because that anchor falls there.",
        rare = true,
    ),

)
