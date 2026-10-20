package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 354: Filocalus's illustrated Roman calendar, with lists of consuls, prefects, bishops and martyrs. */
val db169Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.TOOL,
        section = "Chronology",
        title = "Filocalus's Chronograph of 354, a Roman calendar with lists of consuls and bishops",
        originalTitle = "Chronographus anni 354",
        author = "Furius Dionysius Filocalus",
        year = 354,
        summary = "Furius Dionysius Filocalus compiled this illustrated Roman calendar in 354 for Valentinus, a Christian of Rome, with lists of consuls, city prefects, bishops and martyrs.",
        details = "The original volume is lost, and its pictures survive only in copies, among them drawings made in the seventeenth century. A text copy at St Gall keeps the written lists, so historians rebuild the work's history from the copies.",
        rare = true,
    ),

)
