package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 670 to 680: the scribe's penknife, placed at 674, the year Wearmouth was founded. */
val db137Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.TOOL,
        section = "Scribal",
        title = "The scribe's penknife, a small blade for cutting quills and scraping parchment",
        originalTitle = "penknife",
        author = "Unknown scribes",
        year = 674,
        summary = "The scribe's penknife was a small blade used to cut quill pens and to scrape errors from parchment.",
        details = "The sources do not date any particular penknife, so this record is placed at 674, the year Benedict Biscop founded the monastery of Wearmouth, where books were later copied on parchment. The year 674 is the start of that documented foundation and not the date of any blade.",
        rare = true,
    ),

)
