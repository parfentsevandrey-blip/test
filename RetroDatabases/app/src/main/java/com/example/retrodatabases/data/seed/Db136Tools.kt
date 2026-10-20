package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 680 to 690: Adomnan's De locis sanctis, a Latin guide to the holy places, given to King Aldfrith about 686. */
val db136Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.TOOL,
        section = "Latin",
        title = "Adomnan's De locis sanctis, a Latin guide to the holy places given to King Aldfrith about 686",
        originalTitle = "De locis sanctis",
        author = "Adomnan of Iona",
        year = 686,
        summary = "Adomnan of Iona's De locis sanctis is a Latin guide to the holy places in three books, written from the account of the Gaulish bishop Arculf.",
        details = "Adomnan wrote it in the early 680s, probably between about 683 and 686, and gave it to King Aldfrith of Northumbria, who had reigned since 685. The year 686 is used as the date of the gift, and the dates of the writing are approximate, as the sources usually give them.",
        rare = true,
    ),

)
