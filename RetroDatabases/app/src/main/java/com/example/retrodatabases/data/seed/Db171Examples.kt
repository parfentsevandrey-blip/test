package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 330 to 340: the reign of Constantine the Great, 31 years from his proclamation in 306 to his death in 337, counted in code. */
val db171Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.EXAMPLE,
        section = "Roman",
        title = "The reign of Constantine the Great counted in code, from 306 to his death in 337, a span of 31 years",
        originalTitle = "Constantine's reign",
        author = "Eusebius of Caesarea and the Roman chroniclers",
        year = 337,
        summary = "Constantine ruled from his proclamation in 306 to his death in 337, and a short program counts his 31 years.",
        details = "Constantine was proclaimed in 306 after his father's death at York, and he died at Nicomedia in 337, which is the year used here. The program only subtracts the two years, so a reader can check the 31 against the dates.",
        instructions = """
            1. Save the example as constantine_reign.py.
            2. Run it: python3 constantine_reign.py
        """,
        example = """
            # Constantine's reign, counted from 306 to his death in 337
            ACCESSION = 306
            DEATH = 337
            print("years of Constantine's reign:", DEATH - ACCESSION)
        """,
        rare = true,
    ),

)
