package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 620 to 630: the Chronicon Paschale, a Greek world history built around the reckoning of Easter, dated about 630. */
val db142Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.TOOL,
        section = "Byzantine",
        title = "The Chronicon Paschale, a Greek world history whose title comes from the working out of Easter dates, written about 630",
        originalTitle = "Chronicon Paschale",
        author = "Anonymous",
        year = 630,
        summary = "The Chronicon Paschale is an anonymous Greek world history whose title comes from its work on the dates of Easter, and it is dated to about 630.",
        details = "Its chief surviving manuscript, a tenth-century codex, breaks off in 627, while other references carry the work to 628 or 629, so its end date is disputed. It was probably written at Constantinople, and Burgess and Kulikowski date it to about 630.",
        rare = true,
    ),

)
