package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 440 to 450: the palimpsest, a parchment scraped clean and written over, placed at 440 when Leo the Great became pope. */
val db160Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "The palimpsest, a parchment scraped clean and written over, a common economy of late Roman scribes",
        originalTitle = "Palimpsestos",
        author = "Late Roman and Byzantine scribes",
        year = 440,
        summary = "A palimpsest was a parchment whose older text was scraped off so that the skin could be written again, a common economy of late antique scribes.",
        details = "The sources do not date this practice; 440 is the year of the anchor, the start of Leo's pontificate, and not the year the reuse of parchment began, since the practice is found in books of late antiquity and its beginnings are not dated. The record is placed for that reason.",
        rare = true,
    ),

)
