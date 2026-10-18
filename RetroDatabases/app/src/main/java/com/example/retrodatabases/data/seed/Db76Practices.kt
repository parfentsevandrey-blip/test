package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1280 to 1290: how the Sorbonne college in Paris chained its books and listed them by rank of authority in 1290. */
val db76Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.PRACTICE,
        section = "Libraries",
        title = "The Sorbonne library's chained core and its classification of 1290",
        originalTitle = "Sorbonne library catalogue",
        author = "The masters of the Sorbonne college",
        year = 1290,
        summary = "Around 1289 to 1292 the Sorbonne college in Paris split its books into a chained core and a non-chained set, and its 1290 list ordered them by rank of authority.",
        details = "The 1290 list survives only in fragments, two leaves reused as pastedowns, and most of its contents are known through a later catalogue of 1338. Humbert of Romans had advised around 1270 that some books be chained at the lecterns, and the chained core kept the most used works in one place.",
        rare = true,
    ),

)
