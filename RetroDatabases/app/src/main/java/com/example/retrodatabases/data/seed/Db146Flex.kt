package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 580 to 590: the six years from the Kaihuang code of 583 to the Third Council of Toledo of 589. */
val db146Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Kaihuang code of 583 to the Third Council of Toledo of 589",
        originalTitle = "Years between two dated items",
        author = "Sui and Visigothic compilers",
        year = 583,
        summary = "The Kaihuang code of 583 and the Third Council of Toledo of 589 are six years apart, and a short program prints the gap.",
        details = "The two dates come from different traditions, the Chinese dynastic record and the Visigothic church, so the span compares two dates and does not show that the events were linked. The program only subtracts the years, which a reader can check against the two records.",
        instructions = """
            1. Save the example as code_council_gap.py.
            2. Run it: python3 code_council_gap.py
        """,
        example = """
            KAIHUANG_CODE = 583
            TOLEDO_COUNCIL = 589
            print("years from the Kaihuang code to the Third Council of Toledo:", TOLEDO_COUNCIL - KAIHUANG_CODE)
        """,
    ),

)
