package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 110 to 120: the Ulpian Library in Trajan's Forum, with separate Greek and Latin halls. */
val db193Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.SYSTEM,
        section = "Rome",
        title = "The Ulpian Library in Trajan's Forum, with separate Greek and Latin halls",
        originalTitle = "Bibliotheca Ulpia",
        author = "Emperor Trajan",
        year = 114,
        summary = "The Ulpian Library kept its Greek and Latin books in two separate halls, a division that set the imperial collection out by language.",
        details = "Accounts give 112 as the year Trajan commissioned the library and 114 as the year of its founding, and this record uses 114 for its opening. The Greek and Latin rooms had timber shelves and wall recesses for the scrolls, and one account gives about 20,000 rolls while others call the number uncertain.",
        rare = true,
    ),

)
