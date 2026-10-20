package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 110 to 120: clay seals pressed on the knots of Han documents, placed at 114 for the first year of the Yuanchu era name. */
val db193Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.PRACTICE,
        section = "Documents",
        title = "Clay seals pressed on the knots of Han documents on wood and bamboo",
        originalTitle = "Fengni",
        author = "Han clerks and officials",
        year = 114,
        summary = "Han officials tied their wooden and bamboo documents with cord and sealed the knot with a lump of clay pressed with a seal.",
        details = "The sources do not date this practice; 114 is the first year of the Yuanchu era name, the anchor for the placed year, not the date when the practice began. Clay seals of this kind are known from Han sites, and a seal that was still whole showed that a bundle had not been opened on its way.",
        rare = true,
    ),

)
