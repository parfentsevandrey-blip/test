package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 120 to 130: unit stamps on Roman roof tiles, undated in the sources and placed at 122 for the start of Hadrian's Wall. */
val db192Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.PRACTICE,
        section = "Stamps",
        title = "Unit stamps on Roman tiles, undated in the sources and placed at 122",
        originalTitle = "Tegulae",
        author = "Roman army units",
        year = 122,
        summary = "Roman roof tiles were marked with the name of the unit that made them, so that the output of each unit could be told apart.",
        details = "Stamps of legions and cohorts are found on tiles from the forts along the frontier. The sources do not date the stamps on this frontier; 122, the year Hadrian's Wall was begun, is the year of the anchor, not of the stamping.",
        rare = true,
    ),

)
