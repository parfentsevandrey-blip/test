package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 600 to 610: the Sui census of 609, which counted the households and people of the empire. */
val db144Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.PRACTICE,
        section = "Chinese",
        title = "The Sui census of 609, which counted the households and people of the empire",
        originalTitle = "Daye 5 census",
        author = "Sui court under Emperor Yang",
        year = 609,
        summary = "The Sui court counted about nine million households and 46 million people in 609, a count recorded in the official history of the Sui.",
        details = "The Sui shu gives these figures for the fifth year of the Daye era, which is 609, but some reference tables date the same people figure to 606, so the year is disputed. Historians still debate how complete the count was, since some people were never registered.",
        rare = true,
    ),

)
