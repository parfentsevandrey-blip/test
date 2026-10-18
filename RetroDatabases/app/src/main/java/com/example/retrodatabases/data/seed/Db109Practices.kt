package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 950 to 960: the palace poetry match of Tentoku 4, held beside the Seiryo Hall. */
val db109Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.PRACTICE,
        section = "Poetry",
        title = "The Tentoku palace poetry match of 960, held beside the Seiryo Hall",
        originalTitle = "Tentoku dairi uta-awase",
        author = "Emperor Murakami and the court poets",
        year = 960,
        summary = "A palace poetry match of waka, the Tentoku uta-awase, was held in 960 under Emperor Murakami in spaces beside the Seiryo Hall.",
        details = "In a uta-awase the poems of two sides are set against each other in pairs, and the match of 960 was held in the fourth year of the Tentoku era. The record gives the year and the setting, and a contest of the same kind about a century later, in the Go-Reizei period, was patterned on it.",
        rare = true,
    ),

)
