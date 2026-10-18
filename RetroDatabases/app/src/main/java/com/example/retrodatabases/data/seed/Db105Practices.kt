package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A practice of 998: Odilo of Cluny's day of prayer for the dead on 2 November, with its year in dispute. */
val db105Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.PRACTICE,
        section = "Cluny",
        title = "The 2 November day of prayer for the dead, set at Cluny by Odilo",
        originalTitle = "Commemoratio omnium fidelium defunctorum",
        author = "Odilo of Cluny",
        year = 998,
        summary = "Odilo of Cluny set a day of prayer for the dead on 2 November at Cluny and its houses, and the custom later spread across the western church.",
        details = "The year 998 is the one usually given for Odilo's decree, but other accounts bracket the institution between 998 and 1030, and some place it after 1030, so the date is disputed. The record uses 998, the year most often given.",
    ),

)
