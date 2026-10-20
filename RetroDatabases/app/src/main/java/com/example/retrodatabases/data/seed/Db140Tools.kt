package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 640 to 650: the Kashika-vritti, a seventh-century commentary on Panini's Sanskrit grammar, placed at 650 because it is undated. */
val db140Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.TOOL,
        section = "Indian",
        title = "The Kashika-vritti, a seventh-century commentary on Panini's Sanskrit grammar, placed at 650",
        originalTitle = "Kashika-vritti",
        author = "Vamana and Jayaditya",
        year = 650,
        summary = "The Kashika-vritti, a commentary by Vamana and Jayaditya on Panini's Sanskrit grammar, is undated and is placed here at 650.",
        details = "Scholars date the commentary to the seventh century, with estimates from about 600 to about 660, so the year 650 is a placed year and not a date given by the work. It explains the rules of Panini's grammar, among them the vowel grades guna and vrddhi.",
        rare = true,
    ),

)
