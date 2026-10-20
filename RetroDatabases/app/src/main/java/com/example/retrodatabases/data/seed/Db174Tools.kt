package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 300 to 310: Eusebius's chronological table of world history, whose first edition is traditionally dated to 303. */
val db174Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.TOOL,
        section = "Chronology",
        title = "Eusebius's Chronicle, a chronological table of world history in parallel columns",
        originalTitle = "Chronicon",
        author = "Eusebius of Caesarea",
        year = 303,
        summary = "Eusebius's Chronicle is a chronological table of world history that sets the years of the peoples and kingdoms side by side in columns.",
        details = "The first edition is traditionally dated to 303, but scholars dispute that year, and some put the first edition before 303. Jerome made a Latin version around 380 that carried the list on, and an Armenian version also survives.",
    ),

)
