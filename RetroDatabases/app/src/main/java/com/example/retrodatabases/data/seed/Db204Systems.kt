package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 0 to 10: the Han census of 2, the national count of households and people kept in the Hanshu. */
val db204Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.SYSTEM,
        section = "Population",
        title = "The Han census of 2, whose national totals the Hanshu preserves",
        originalTitle = "Hanshu geography treatise, census of 2",
        author = "The Han imperial administration",
        year = 2,
        summary = "The Han census of 2 recorded 12,366,470 households and 59,594,978 people for the whole empire, and the totals are given in the geography treatise of the Hanshu.",
        details = "The census was taken in the Yuanshi era of Emperor Ping, and the Hanshu gives its totals commandery by commandery. Accounts differ on the number of people: the stated total is 59,594,978, while other accounts give about 57.67 million, and the gap is not settled.",
        rare = true,
    ),

)
