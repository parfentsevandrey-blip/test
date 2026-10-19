package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 770 to 780: the 143 years from Brahmagupta's treatise of 628 to its Arabic Sindhind of 771. */
val db127Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Brahmasphutasiddhanta of 628 to its Arabic Sindhind of 771, a gap of 143 years",
        originalTitle = "Years between a treatise and its Arabic version",
        author = "Brahmagupta",
        year = 771,
        summary = "The Brahmasphutasiddhanta of 628 and its Arabic Sindhind of 771 are 143 years apart, and a short program prints that gap.",
        details = "Brahmagupta finished the treatise in 628, and the Arabic version is dated to the early 770s, with 771 used here. The span is a plain subtraction of two dates, and it says nothing about how the translation was made or how long it took.",
        rare = true,
        instructions = """
            1. Save the example as sindhind_gap.py.
            2. Run it: python3 sindhind_gap.py
        """,
        example = """
            BRAHMAGUPTA = 628  # the Brahmasphutasiddhanta, as commonly dated
            SINDHIND = 771     # the Arabic Sindhind, as used here
            print("years from the Brahmasphutasiddhanta to the Sindhind:", SINDHIND - BRAHMAGUPTA)
        """,
    ),

)
