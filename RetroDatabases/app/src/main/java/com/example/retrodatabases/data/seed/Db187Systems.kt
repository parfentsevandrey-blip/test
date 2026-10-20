package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 178: the sale of Han official posts under Emperor Ling, with the dates and the grading that accounts give. */
val db187Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.SYSTEM,
        section = "Offices",
        title = "The sale of official posts under Emperor Ling of the Han",
        originalTitle = "maiguan",
        author = "Emperor Ling of the Han and his court",
        year = 178,
        summary = "By the commonly given date of 178, Emperor Ling of the Han began selling official posts for money, with prices that differed by rank.",
        details = "Accounts date the start of the sales to 177 or 178, and they disagree on the scale of the sales and on how much of the money reached the treasury. The record therefore gives no sums, only the fact of the sales and their grading by rank.",
        rare = true,
    ),

)
