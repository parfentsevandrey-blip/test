package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 500 to 510: the years of Alaric II's reign, from his accession in 484 to his death in 507, counted in code. */
val db154Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.EXAMPLE,
        section = "Visigothic",
        title = "Alaric II's reign from 484 to 507, a span of 23 years counted in code",
        originalTitle = "Breviarium Alaricianum",
        author = "Chroniclers of the Visigothic and Frankish kingdoms",
        year = 507,
        summary = "Counted in code, the reign of Alaric II, king of the Visigoths from 484 until his death at Vouille in 507, lasted 23 years.",
        details = "The accession year 484 and the death year 507 are the usual dates, and the Breviary of 506 falls 22 years into the reign. The program only subtracts the years, so a reader can check each figure against the two dates.",
        instructions = """
            1. Save the example as alaric_reign.py.
            2. Run it: python3 alaric_reign.py
        """,
        example = """
            ACCESSION = 484  # Alaric II becomes king of the Visigoths, the usual date
            DEATH = 507      # Alaric II falls at Vouille
            BREVIARY = 506   # the Breviary issued in Alaric's name
            print("years of the reign:", DEATH - ACCESSION)
            print("years into the reign at the Breviary:", BREVIARY - ACCESSION)
        """,
    ),

)
