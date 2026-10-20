package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 680 to 690: the years from the Synod of Hatfield of 680 to the Asuka Kiyomihara code of 689. */
val db136Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Synod of Hatfield of 680 to the Asuka Kiyomihara code of 689",
        originalTitle = "Years between two dated records",
        author = "Theodore of Tarsus and the Japanese court",
        year = 680,
        summary = "The Synod of Hatfield of 680 and the Kiyomihara code of 689 are nine years apart, and a short program prints the gap.",
        details = "The two items are unrelated, so the span only compares the dates usually given for them: 680 for the synod, as the Anglo-Saxon Chronicle gives it, and 689, the year of promulgation, for the code. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as hatfield_kiyomihara.py.
            2. Run it: python3 hatfield_kiyomihara.py
        """,
        example = """
            HATFIELD = 680    # the Synod of Hatfield, as usually dated
            KIYOMIHARA = 689  # the Kiyomihara code, promulgated in 689
            print("years from the Synod of Hatfield of 680 to the Kiyomihara code of 689:", KIYOMIHARA - HATFIELD)
        """,
    ),

)
