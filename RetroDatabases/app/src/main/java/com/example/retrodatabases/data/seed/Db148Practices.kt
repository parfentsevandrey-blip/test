package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 560 to 570: the new letters that Chilperic added to the Latin alphabet, as Gregory of Tours reports, placed at 561. */
val db148Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.PRACTICE,
        section = "Frankish",
        title = "Chilperic's added letters for the Latin alphabet, which Gregory of Tours reports the Frankish king introduced",
        originalTitle = "novae litterae (new letters)",
        author = "Gregory of Tours",
        year = 561,
        summary = "Gregory of Tours reports that the Frankish king Chilperic added new letters to the Latin alphabet.",
        details = "The sources used here do not date the change, so 561 is only the anchor for this placed record: it is the year Chilperic's share of his father Chlothar's kingdom began, not the year of the letters. Gregory's history is the main account of the change.",
        rare = true,
    ),

)
