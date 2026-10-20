package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 560 to 570: Sigebert I's reign of 14 years, from 561 to his death in 575, counted in code. */
val db148Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.EXAMPLE,
        section = "Frankish",
        title = "The reign of Sigebert I of Austrasia counted in code, from 561 to his death in 575, a span of 14 years",
        originalTitle = "Sigebert's reign",
        author = "Gregory of Tours and the Frankish chroniclers",
        year = 561,
        summary = "Sigebert I ruled Austrasia from 561 to his death in 575, and a short program counts its 14 years.",
        details = "Sigebert received Austrasia when his father Chlothar I died in 561, and he was killed in 575, which is the year used here for the end. The program only subtracts the two years, so a reader can check the 14 against the dates.",
        instructions = """
            1. Save the example as sigebert_reign.py.
            2. Run it: python3 sigebert_reign.py
        """,
        example = """
            # Sigebert I's reign in Austrasia, counted from 561 to his death in 575
            ACCESSION = 561
            DEATH = 575
            print("years of Sigebert's reign:", DEATH - ACCESSION)
        """,
    ),

)
