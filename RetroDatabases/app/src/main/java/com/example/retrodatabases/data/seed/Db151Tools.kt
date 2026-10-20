package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 530 to 540: the Chronicle of Marcellinus Comes, a Latin year-by-year record from 379 to 534 used for dating events. */
val db151Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.TOOL,
        section = "Latin",
        title = "The chronicle of Marcellinus Comes, a Latin year-by-year record from 379 to 534 for dating events",
        originalTitle = "Chronicon",
        author = "Marcellinus Comes",
        year = 534,
        summary = "Marcellinus Comes, a Latin writer at Constantinople, carried his year-by-year chronicle from 379 to 534 as a reference for fixing the dates of events.",
        details = "The first version ended in 518, and a continuation carried the chronicle to 534, which is the span usually cited. Continuations to 548 or 566 are reported, and some scholars deny that the part after 534 is Marcellinus's own work, while the chronicle also has factual slips.",
        rare = true,
    ),

)
