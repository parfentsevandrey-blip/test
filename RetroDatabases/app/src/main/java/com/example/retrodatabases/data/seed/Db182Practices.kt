package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 220 to 230: Hippolytus's table of sixteen-year Paschal cycles, inscribed on the seat of a statue, from 222. */
val db182Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.PRACTICE,
        section = "Easter",
        title = "Hippolytus's sixteen-year table of Paschal cycles, dated from 222",
        originalTitle = "Paschal table",
        author = "Hippolytus of Rome, as attributed",
        year = 222,
        summary = "Hippolytus's Paschal table of sixteen-year cycles, inscribed on the seat of a statue, set out the Easter dates from 222 onward.",
        details = "The seat's table runs seven spans of sixteen years, covering 222 to 333, and its heading ties the first span to the first regnal year of Alexander Severus, which is 222. The identity of the statue is disputed, and the table's dates drift from the true Easter dates as the table runs on.",
        rare = true,
    ),

)
