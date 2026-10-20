package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 274: the officina numbers on Roman coins, which name the workshop that struck each piece. */
val db177Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.PRACTICE,
        section = "Coinage",
        title = "The officina numbers on Roman coins, which name the workshop that struck each piece",
        originalTitle = "Officina",
        author = "The imperial mints",
        year = 274,
        summary = "Coins struck at Ticinum in 274 carry officina numbers, so each coin names the workshop that struck it and the output of each workshop can be counted.",
        details = "The issues of Ticinum for 274 carry officina numbers 1 to 3 across the year, each number marking one workshop within the mint. Whether the numbering began in this year or earlier is not settled, so 274 is the year of these issues and not a proven start for the system.",
        rare = true,
    ),

)
