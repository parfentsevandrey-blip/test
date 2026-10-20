package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 90 to 100: how an AD year becomes a year counted from the founding of Rome, with 96 as the worked year. */
val db195Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.GUIDE,
        section = "Dating",
        title = "How an AD year becomes a year counted from the founding of Rome, with 96 as the worked case",
        originalTitle = "Ab urbe condita",
        author = "Varro and Roman chroniclers",
        year = 96,
        summary = "An AD year becomes a Roman year counted from the founding of the city by adding 753, and a short program converts four years of the decade.",
        details = "The method is the Roman count from the founding of the city, in the chronology that Varro used to place the founding in 753 BC, and other Roman writers gave other founding years, so the count depends on the chronology chosen. The year 96 is used as the worked case because it is the year of Domitian's assassination in most references, and the example also prints the founding year itself.",
        rare = true,
        instructions = """
            1. Save the example as ab_urbe_condita.py.
            2. Run it: python3 ab_urbe_condita.py
        """,
        example = """
            def auc_year(ad_year):
                # AD 1 is AUC 754, so an AD year is converted by adding 753.
                return ad_year + 753

            for year in [90, 96, 98, 100]:
                print(f"AD {year} is AUC {auc_year(year)}")
            print("753 BC is AUC", 754 - 753)
        """,
    ),

)
