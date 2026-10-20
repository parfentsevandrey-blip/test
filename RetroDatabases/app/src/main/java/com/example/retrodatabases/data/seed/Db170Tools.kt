package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 343: a pair of dividers, undated in the sources and placed at the year of the Council of Serdica. */
val db170Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.TOOL,
        section = "Drawing",
        title = "A pair of dividers for drawing circles, undated in the sources and placed at 343, the year of the Council of Serdica",
        originalTitle = "Dividers",
        author = "Greek and Roman geometers",
        year = 343,
        summary = "A pair of dividers, two legs joined at a hinge, lets a draughtsman set a fixed radius and draw circles or carry a length from one place to another.",
        details = "The sources do not date this tool, so 343 is the year of the anchor, the Council of Serdica, and not the year of the dividers' first use. The instrument is far older than this decade, and geometers of the period drew their circles with such legs.",
        rare = true,
    ),

)
