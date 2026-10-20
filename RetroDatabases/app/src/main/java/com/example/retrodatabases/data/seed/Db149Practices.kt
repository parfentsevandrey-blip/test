package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 550 to 560: the silkworm eggs that two monks brought to Constantinople under Justinian, about 552. */
val db149Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.PRACTICE,
        section = "Byzantine",
        title = "The silkworm eggs that two monks brought to Constantinople under Justinian, about 552, as Procopius tells it",
        originalTitle = "silkworm eggs",
        author = "Procopius of Caesarea, who reports the story",
        year = 552,
        summary = "According to Procopius, two monks smuggled silkworm eggs into the empire under Justinian, and silk-making in the empire is said to have begun from them.",
        details = "Most accounts give about 552 for the episode, but the dates offered run from 552 to 563, and the story rests mainly on Procopius. Historians question parts of the account, so the record keeps only its outline and not the details of the route or the concealment.",
        rare = true,
    ),

)
