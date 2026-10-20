package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 640 to 650: the years from the Edict of Rothari of 643 to the Taika reform edict of 646. */
val db140Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Edict of Rothari of 643 to the Taika reform edict of 646",
        originalTitle = "Edictus Rothari and Taika no kaishin",
        author = "Rothari and the Japanese court",
        year = 646,
        summary = "The Edict of Rothari of 643 and the Taika reform edict of 646 are three years apart, and a short program prints the gap.",
        details = "The two laws come from Lombard Italy and from Japan, which had no direct contact, so the span only compares the dates usually given for them. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as rothari_taika.py.
            2. Run it: python3 rothari_taika.py
        """,
        example = """
            ROTHARI = 643  # the Edict of Rothari, issued in 643
            TAIKA = 646    # the Taika reform edict, dated 646 in the Nihon Shoki
            print("years from the Edict of Rothari to the Taika reform edict:", TAIKA - ROTHARI)
        """,
    ),

)
