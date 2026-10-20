package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 330 to 340: the 25 December date of Christ's birth in the Roman calendar of 336, kept in the Chronograph of 354. */
val db171Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.PRACTICE,
        section = "Feasts",
        title = "The Roman calendar entry that names 25 December as the birth of Christ at Bethlehem",
        originalTitle = "natus Christus in Betleem Iudeae",
        author = "The compiler of the Roman calendar of 336",
        year = 336,
        summary = "The Roman calendar of 336, preserved in the Chronograph of 354, names 25 December as the day of Christ's birth at Bethlehem.",
        details = "The entry is dated by the eighth day before the January kalends, the Roman way of counting the days, and it stands in the list of martyrs' commemoration dates. Accounts differ on whether this part of the text belongs to the calendar of 336 or only to the manuscript of 354 that keeps it, and 336 is the year used here.",
        rare = true,
    ),

)
