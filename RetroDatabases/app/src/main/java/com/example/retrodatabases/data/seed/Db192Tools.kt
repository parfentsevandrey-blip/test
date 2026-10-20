package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 120 to 130: a bronze hand abacus of the Roman world, undated in the sources and placed at 122 for the start of Hadrian's Wall. */
val db192Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.TOOL,
        section = "Counting",
        title = "A bronze hand abacus of the Roman world, undated in the sources and placed at 122",
        originalTitle = "Abacus",
        author = "Roman reckoners and merchants",
        year = 122,
        summary = "A small bronze hand abacus has grooves and loose counters that hold the places of a sum, so the reckoner can set out and move the figures by hand.",
        details = "The sources do not date this tool; 122, the year Hadrian's Wall was begun, is the year of the anchor, not of the making. The grooves stand for the places of a sum, and the tool is a different object from the flat counting board that is set out on a table.",
        rare = true,
    ),

)
