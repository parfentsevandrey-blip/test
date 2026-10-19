package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 720 to 730: Yi Xing's Dayan calendar, issued in 729, and Bede's De temporum ratione of 725 on the reckoning of time and Easter. */
val db132Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "Yi Xing's Dayan calendar of the Tang, issued by imperial order in 729",
        originalTitle = "Dayan li",
        author = "Yi Xing",
        year = 729,
        summary = "Yi Xing's Dayan calendar, a Tang calendar reform, is dated here to its imperial issue of 729.",
        details = "Yi Xing, who devised the calendar, died in 727, and accounts differ on its completion, which some give as 727 and others as 728. The date used here is the imperial issue of 729.",
        rare = true,
    ),

    entry(
        databaseId = 132,
        kind = Kind.TOOL,
        section = "Latin",
        title = "Bede's De temporum ratione, a handbook on the reckoning of time and Easter, written in 725",
        originalTitle = "De temporum ratione",
        author = "Bede",
        year = 725,
        summary = "Bede's De temporum ratione, a Latin handbook on the reckoning of time, the calendar and Easter, was written in 725.",
        details = "It sets out the reckoning of days, months and years and the rule for the date of Easter, written for Latin readers. The date 725 is the usual one for the work.",
    ),

)
