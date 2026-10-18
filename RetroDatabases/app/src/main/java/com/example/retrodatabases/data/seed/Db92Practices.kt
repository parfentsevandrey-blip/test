package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 1120 to 1130: the Pipe Roll of 1130, the oldest surviving English royal account roll. */
val db92Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.PRACTICE,
        section = "Accounts",
        title = "The Pipe Roll of 1130, the oldest surviving English royal account roll",
        originalTitle = "Magnum Rotulum Pipae",
        author = "The Exchequer of Henry I",
        year = 1130,
        summary = "The Pipe Roll of 1130, dated by the thirty-first regnal year of Henry I, is the oldest surviving English royal account roll, a yearly record of the sheriffs' payments.",
        details = "Each year the Exchequer kept the sheriffs' accounts on a long roll of parchment, and this roll, dated to 1130, is the earliest of the series that survives. The date comes from the regnal year, so the calendar year is approximate and depends on how that year is reckoned.",
    ),

)
