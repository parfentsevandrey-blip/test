package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 890 to 900: the official Japanese embassies to Tang China, which ended after Sugawara no Michizane proposed in 894 that they be stopped. */
val db115Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "The Japanese embassies to Tang China, ended by Sugawara no Michizane's proposal of 894",
        originalTitle = "Kentoshi",
        author = "Sugawara no Michizane",
        year = 894,
        summary = "The official Japanese embassies to Tang China ended after Sugawara no Michizane proposed in 894 that they be stopped.",
        details = "Michizane argued that Tang China was in decline and that the crossing was dangerous. The mission named for 894 never sailed, and no official embassy to the Tang court was sent after it.",
    ),

)
