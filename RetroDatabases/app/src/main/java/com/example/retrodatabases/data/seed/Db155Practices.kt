package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 490 to 500: a ruling house of the Northern Wei takes a Chinese surname in 496. */
val db155Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.PRACTICE,
        section = "Names",
        title = "The Tuoba imperial house takes the Chinese surname Yuan in 496",
        originalTitle = "Yuan xing",
        author = "Emperor Xiaowen of the Northern Wei",
        year = 496,
        summary = "In 496 the ruling house of the Northern Wei, which had carried the Xianbei name Tuoba, took the Chinese surname Yuan.",
        details = "Sources describe the change for the imperial house, and accounts differ on how far the order reached beyond it. It was one of the naming reforms of Emperor Xiaowen, a practice of renaming a family to fit Chinese usage.",
        rare = true,
    ),

)
