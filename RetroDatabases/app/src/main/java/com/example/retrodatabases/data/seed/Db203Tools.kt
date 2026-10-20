package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 10 to 20: bronze tweezers, undated in the sources and placed at 15 with Germanicus's campaign across the Rhine. */
val db203Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.TOOL,
        section = "Household",
        title = "Bronze tweezers from Roman sites, a pair of arms joined at one end",
        originalTitle = "Volsella",
        author = "Roman metalworkers",
        year = 15,
        summary = "Bronze tweezers with two arms joined at one end are common finds from Roman sites, and no text dates the pairs, so the year 15 is placed here as an anchor.",
        details = "The sources do not date this tool; 15 is the year of the anchor, Germanicus's campaign across the Rhine against the Chatti, not of the making. Pairs of this shape turn up in many Roman sites, and the record gives only the tool and the anchor, since no closer date can be given.",
        rare = true,
    ),

)
