package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 30 to 40: the tabellarii, Roman couriers who carried letters, placed at the death of Tiberius because no dated example survives. */
val db201Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.PRACTICE,
        section = "Letters",
        title = "Tabellarii, the Roman couriers who carried letters, undated in the sources and placed at 37",
        originalTitle = "tabellarii",
        author = "Roman letter-writers",
        year = 37,
        summary = "The tabellarii were Roman couriers who carried letters from one writer to another, often written on wax-covered tablets.",
        details = "The sources do not date any particular courier, so 37, the year Tiberius died, is the year of the anchor and not of the practice. Letter-carriers were in use in the late Republic and went on under the emperors, so the practice has no single date of its own.",
        rare = true,
    ),

)
