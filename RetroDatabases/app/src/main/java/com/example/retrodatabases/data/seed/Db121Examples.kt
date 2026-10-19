package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Example of 836: the years Samarra served as the Abbasid capital, counted in code. */
val db121Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.EXAMPLE,
        section = "Iraq",
        title = "The years Samarra served as the Abbasid capital, from 836 to 892, counted in code",
        originalTitle = "Sur man ra'a",
        author = "Al-Mu'tasim, Abbasid caliph",
        year = 836,
        summary = "Samarra served as the Abbasid capital from 836 to 892, a span of 56 years, and a short program counts the span.",
        details = "Al-Mu'tasim moved the court to the new city of Samarra in 836, and the capital returned to Baghdad in 892 under al-Mu'tadid. The example prints the span between these two years and the span from 836 to 900, using the dates as commonly given.",
        instructions = """
            1. Save the example as samarra_years.py.
            2. Run it: python3 samarra_years.py
        """,
        example = """
            CAPITAL_FROM = 836   # al-Mu'tasim moves the court to Samarra, as commonly given
            CAPITAL_TO = 892     # the court returns to Baghdad, as commonly given
            print("years Samarra was the capital:", CAPITAL_TO - CAPITAL_FROM)
            print("years from 836 to 900:", 900 - CAPITAL_FROM)
        """,
    ),

)
