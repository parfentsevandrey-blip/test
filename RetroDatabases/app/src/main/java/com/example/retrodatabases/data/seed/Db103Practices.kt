package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1010: the questions a priest put to a penitent, as set out in Burchard of Worms's Corrector. */
val db103Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.PRACTICE,
        section = "Penance",
        title = "Burchard of Worms's Corrector, a list of questions a confessor put to penitents",
        originalTitle = "Corrector sive medicus",
        author = "Burchard of Worms",
        year = 1010,
        summary = "Burchard of Worms's Corrector, the nineteenth book of his Decretum, is a long list of questions that a confessor could put to a penitent before setting a penance.",
        details = "The Decretum is usually dated 1008 to 1012, and the record uses 1010 within that range. The questions range over many kinds of sin, and they were meant to help priests hear confession in a parish.",
        rare = true,
    ),

)
