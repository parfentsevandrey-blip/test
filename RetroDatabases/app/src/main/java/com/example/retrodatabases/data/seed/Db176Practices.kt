package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 280 to 290: the qiju zhu court diary, a record-keeping practice older than the Jin and placed at 285 by Wani. */
val db176Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.PRACTICE,
        section = "Court records",
        title = "The qiju zhu, a court diary of the ruler's words and conduct kept by palace attendants",
        originalTitle = "qiju zhu",
        author = "Palace attendants",
        year = 285,
        summary = "The qiju zhu was a court diary that logged the ruler's words and conduct, kept by palace attendants as a daily record of the court.",
        details = "The practice is older than the Jin, and the records of the daily life of the Han emperor Xian are an early example, so the year 285 is the year of the anchor, Wani's traditional arrival, and not the year the practice began. Jin diaries are known mainly through quotations in later works.",
        rare = true,
    ),

)
