package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 927: the Oharae purification, read twice a year, with its prayer set down in the Engishiki of that year. */
val db112Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.PRACTICE,
        section = "Japan",
        title = "The Oharae purification, read twice a year from a prayer set down in the Engishiki",
        originalTitle = "Oharae no kotoba",
        author = "The compilers of the Engishiki",
        year = 927,
        summary = "The Oharae, the court's great purification, was held twice a year, and its prayer is set down in the Engishiki, completed in 927.",
        details = "The rite is older than the written prayer, which is one of the twenty-seven norito prayers in the eighth book of the Engishiki. The rite was held at the end of the sixth and twelfth months, and 927 is the year the Engishiki, which records the prayer, was completed.",
        rare = true,
    ),

)
