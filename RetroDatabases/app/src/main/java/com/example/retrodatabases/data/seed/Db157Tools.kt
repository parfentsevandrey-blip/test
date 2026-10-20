package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 470 to 480: gold coin dies of the Constantinople mint, a placed record at 474 because the sources do not date them. */
val db157Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.TOOL,
        section = "Coinage",
        title = "Gold coin dies of the Constantinople mint under Zeno",
        originalTitle = "Gold coin dies of the Constantinople mint under Zeno",
        author = "The moneyers of the Constantinople mint",
        year = 474,
        summary = "Engraved metal dies struck the gold solidus at the Constantinople mint, and the sources give no date for the dies themselves.",
        details = "The sources do not date these dies; 474 is the year of the anchor, the start of Zeno's reign at Constantinople after the death of Leo II, and not the date of the dies. The record is placed so that a tool of the decade is present, and it names no particular die.",
        rare = true,
    ),

)
