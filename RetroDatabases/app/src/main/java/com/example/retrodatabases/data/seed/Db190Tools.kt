package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of the 140s: Roman dice of bone, placed at 140 with Marcion's arrival at Rome. */
val db190Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.TOOL,
        section = "Chance",
        title = "Roman dice of bone, pipped cubes for games of chance",
        originalTitle = "Tesserae",
        author = "Roman players",
        year = 140,
        summary = "Roman dice were small cubes of bone, marked with pips, that players threw in games of chance.",
        details = "Roman players threw small cubes of bone, marked with pips on their faces, in games of chance. The sources do not date these dice; 140 is the year of the anchor, Marcion's arrival at Rome, not of the making.",
        rare = true,
    ),

)
