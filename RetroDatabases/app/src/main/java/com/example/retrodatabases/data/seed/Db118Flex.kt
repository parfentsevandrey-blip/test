package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 860 to 870: the years from the Edict of Pitres to the Diamond Sutra, and from Basil I's sole rule to the end of the Macedonian line. */
val db118Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Edict of Pitres to the printing of the Diamond Sutra",
        originalTitle = "Years between two dated items",
        author = "Charles the Bald and Wang Jie",
        year = 864,
        summary = "The Edict of Pitres of 864 and the Diamond Sutra colophon of 868 are four years apart, and a short program prints the span.",
        details = "Both dates are the ones usually given: late June 864 for the edict and 868 for the colophon's printing date. The example takes the year of each and prints the difference, so the span links two events far apart in Eurasia.",
        instructions = """
            1. Save the example as pitres_diamond.py.
            2. Run it: python3 pitres_diamond.py
        """,
        example = """
            PITRES = 864    # Charles the Bald's Edict of Pitres, issued in June 864
            DIAMOND = 868   # the colophon year of the Diamond Sutra
            print("years from the Edict of Pitres to the Diamond Sutra:", DIAMOND - PITRES)
        """,
    ),

    entry(
        databaseId = 118,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Basil I's sole rule to the death of Theodora, the last Macedonian ruler",
        originalTitle = "Years between two dynastic dates",
        author = "Basil I and Theodora",
        year = 867,
        summary = "Basil I's sole rule began in 867 and the Macedonian line ended with Theodora in 1056, and a short program prints the 189 years between the two dates.",
        details = "Both dates are the ones usually given, and the example prints the difference between them. The difference is a length of time only and says nothing about the emperors who ruled in between.",
        instructions = """
            1. Save the example as macedonian_span.py.
            2. Run it: python3 macedonian_span.py
        """,
        example = """
            BASIL_SOLE = 867   # Basil I's sole rule, as commonly given
            LAST_RULER = 1056  # the death of Theodora, the last of the Macedonian line
            print("years from Basil I's sole rule to the end of the line:", LAST_RULER - BASIL_SOLE)
        """,
    ),

)
