package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 20 to 30: finger numerals, the Latin way of showing numbers on the hands, placed at 23 by the anchor of the death of Drusus. */
val db202Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.PRACTICE,
        section = "Counting",
        title = "Finger numerals, the way Latin writers counted on the hands",
        originalTitle = "Finger numerals",
        author = "No single author",
        year = 23,
        summary = "Finger numerals show numbers with the fingers and hands, a way of counting that Latin writers mention from comedy onward.",
        details = "The practice has no single founding date, so 23 is the year of the anchor, the death of Drusus Julius Caesar, and not the year finger counting began. Latin texts from Plautus onward refer to counting on the fingers, and the full scheme for the hands is set out in Bede's De temporum ratione of 725.",
        rare = true,
    ),

)
