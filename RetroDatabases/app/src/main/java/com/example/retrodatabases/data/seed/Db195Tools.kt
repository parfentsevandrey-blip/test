package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 90 to 100, placed at 98: the Roman juror's ballot tablet marked A, C and NL, with its anchor named. */
val db195Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.TOOL,
        section = "Courts",
        title = "The Roman juror's ballot tablet, marked A, C and NL for acquittal, condemnation and not clear",
        originalTitle = "Tabella",
        author = "Roman jurors",
        year = 98,
        summary = "Roman jurors marked a small tablet with A, C or NL to vote for acquittal, for condemnation or for a verdict that left the case open, and the tablets were counted to give the result.",
        details = "The sources do not date the first juror's tablet, although the letters A, C and NL are attested in Roman court procedure, and NL stood for non liquet, not clear. This record is placed at 98, the year Nerva died, which most references give as 27 January 98, though one account gives 25 January, and the year is the anchor and not the date of any tablet. The verdict came from counting the marked tablets, so the object belongs with the counting records.",
        rare = true,
    ),

)
