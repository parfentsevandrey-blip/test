package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 540 to 550: the Chronicle of Edessa, a Syriac local chronicle that runs from a flood of 201 to the year 540. */
val db150Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.TOOL,
        section = "Syriac",
        title = "The Chronicle of Edessa, a Syriac local chronicle of the city's bishops and floods, running from 201 to 540",
        originalTitle = "Chronicon Edessenum",
        author = "Anonymous Syriac author of Edessa",
        year = 540,
        summary = "The Chronicle of Edessa is a Syriac record of one city's events, arranged by year, from a flood of 201 to the year 540.",
        details = "The year 540 marks where the coverage ends, not the date of writing: Brock places the work soon after 540, while a rival view pushes it past 570, so accounts differ. Most entries concern the bishops of Edessa, and Hun incursions and dealings between Byzantium and Persia receive only passing mention.",
        rare = true,
    ),

)
