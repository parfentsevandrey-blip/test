package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 20 to 30: the yearly statistical reports of Han local offices, placed at 25 by the anchor of the Eastern Han. */
val db202Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.SYSTEM,
        section = "Administration",
        title = "The yearly statistical reports that Han local offices sent to the capital",
        originalTitle = "Shangji",
        author = "Han local and central officials",
        year = 25,
        summary = "Han local offices sent yearly statistical reports to the central court, a counting and record-keeping system of the Han government.",
        details = "The sources do not date this system, so 25 is the year of the anchor, the restoration of the Han under Liu Xiu as Emperor Guangwu, and not the year the reports began. Bamboo slips found at Yinwan hold a commandery's annual statistical report meant for the central court.",
        rare = true,
    ),

)
