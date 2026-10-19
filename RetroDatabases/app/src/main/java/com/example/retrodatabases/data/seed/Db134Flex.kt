package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 700 to 710: the years from the household registers of 702 to the completion of the Shitong in 710. */
val db134Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the household registers of 702 to the completion of the Shitong in 710",
        originalTitle = "Years between two East Asian records",
        author = "Liu Zhiji",
        year = 702,
        summary = "The household registers dated 702 and the Shitong completed in 710 are eight years apart, and a short program prints the gap.",
        details = "The two items are unrelated, so the span only compares the dates usually given for them. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as registers_shitong.py.
            2. Run it: python3 registers_shitong.py
        """,
        example = """
            REGISTERS = 702  # the household registers dated 702, as commonly given
            SHITONG = 710    # the Shitong completed in 710, as commonly given
            print("years from the registers of 702 to the Shitong of 710:", SHITONG - REGISTERS)
        """,
    ),

)
