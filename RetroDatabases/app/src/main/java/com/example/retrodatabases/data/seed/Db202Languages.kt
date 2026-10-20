package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 20 to 30: Strabo's Geographica, a Greek geography in seventeen books completed in the 20s. */
val db202Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Strabo's Geographica, a Greek geography in seventeen books",
        originalTitle = "Geographika",
        author = "Strabo of Amaseia",
        year = 23,
        summary = "Strabo's Geographica describes the inhabited world in seventeen Greek books and reckons its distances in stades.",
        details = "Accounts differ on the exact year the Geographica was completed, and this record uses 23 as the completion year. Books 1 and 2 are introductory, books 3 to 10 cover Europe, books 11 to 16 cover Asia, and book 17 covers Africa and Egypt.",
    ),

)
