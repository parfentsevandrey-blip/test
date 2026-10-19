package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 700 to 710: Aldhelm's Latin riddles, placed at 705, the year he became bishop of Sherborne. */
val db134Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.TOOL,
        section = "Latin",
        title = "Aldhelm's Enigmata, a Latin book of riddles in verse, placed at 705 with his bishopric of Sherborne",
        originalTitle = "Aenigmata",
        author = "Aldhelm of Malmesbury",
        year = 705,
        summary = "Aldhelm's Aenigmata is a Latin collection of about a hundred riddles in verse, each describing a thing for the reader to name.",
        details = "The sources do not date the riddles; 705 is the year Aldhelm became bishop of Sherborne, the anchor used here, not the year they were made. Aldhelm had been abbot at Malmesbury before that office.",
        rare = true,
    ),

)
