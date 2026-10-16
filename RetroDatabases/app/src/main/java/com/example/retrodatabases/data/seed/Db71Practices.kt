package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1330: the Spanish era, a dating system that counted from 38 BC. */
val db71Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.PRACTICE,
        section = "Dating",
        title = "Dating a document by the Spanish era, which began 38 years before the Christian count",
        originalTitle = "Era hispanica",
        author = "The chanceries of Castile and Aragon",
        year = 1330,
        summary = "Documents in Castile and Aragon were dated by the Spanish era, which counted from 38 BC, so that an era year minus 38 gives the Christian year.",
        details = "The era was used in Castilian and Aragonese documents for many centuries, and its use faded in the later fourteenth century. The year 1330 is used here as the Christian year of the era year 1368.",
    ),

)
