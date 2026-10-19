package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 740 to 750: the sectioned clay moulds of the Great Buddha casting, placed at the decree of 743. */
val db130Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.TOOL,
        section = "Japanese",
        title = "The sectioned clay moulds, fired in an oven, that shaped the bronze of the Great Buddha at Nara",
        originalTitle = "Clay moulds of the Daibutsu",
        author = "Unknown Nara moulders",
        year = 743,
        summary = "The bronze of the Great Buddha was cast in sections in clay moulds that were fired in an oven, and the metal was poured in stages from the base.",
        details = "The sources do not date these moulds; 743 is the year of the decree that began the project, not the year of the casting. Accounts differ on when the casting began, so the record is placed at the decree's year as an anchor.",
        rare = true,
    ),

)
