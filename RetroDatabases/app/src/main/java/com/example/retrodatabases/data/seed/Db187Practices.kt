package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 177: word dots in Latin inscriptions, a placed record whose year is the persecution at Lyons. */
val db187Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "The word dots of Latin inscriptions, points that divide one word from the next",
        originalTitle = "interpuncta",
        author = "The stonecutters of Rome and the Latin provinces",
        year = 177,
        summary = "Latin inscriptions set a point between words so that a reader could tell where one word ended and the next began.",
        details = "This is a placed record: the practice is far older than 177, so 177 is the year of the anchor, the persecution at Lyons, and not the year the practice began. The dots appear in some inscriptions and not in others, so the practice was never universal.",
        rare = true,
    ),

)
