package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 50 to 59: Paul's two letters to the Corinthians, which set a weekly collection and its carriers in writing. */
val db199Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.LANGUAGE,
        section = "Letters",
        title = "Paul's letters to the Corinthians, on a weekly collection carried by named men",
        originalTitle = "Pros Korinthious",
        author = "Paul the apostle, with Sosthenes named as co-sender",
        year = 54,
        summary = "Paul's two letters to the Corinthians, written from Ephesus and from Macedonia in the 50s, set out a weekly collection for Jerusalem and the men chosen to carry it.",
        details = "First Corinthians is usually dated to about 53 to 55 and Second Corinthians to a year or so later, and scholars differ on both dates, so 54 is the year given for the first letter. First Corinthians 16:1-2 asks each person to set money aside on the first day of the week, and Second Corinthians 8:18-21 describes the brothers sent to carry the gift, so that no one could blame Paul's team for handling it. The first letter closes with a greeting in Paul's own hand (1 Corinthians 16:21).",
    ),

)
