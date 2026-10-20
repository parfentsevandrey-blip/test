package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 660 to 670: the years from the Synod of Whitby of 664 to the Kogo register of 670. */
val db138Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Synod of Whitby of 664 to the Kogo register of 670",
        originalTitle = "Whitby and Kogo-nen-jaku",
        author = "King Oswiu and Emperor Tenji",
        year = 664,
        summary = "The Synod of Whitby of 664 and the Kogo register of 670 are six years apart, and a one-line program prints the gap.",
        details = "The two items have nothing to do with each other, so the figure only compares the dates usually given for them. A reader can check the printed gap against the two years.",
        instructions = """
            1. Save the example as whitby_kogo.py.
            2. Run it: python3 whitby_kogo.py
        """,
        example = """
            WHITBY = 664  # the Synod of Whitby, as usually dated
            KOGO = 670    # the Kogo register of 670, as usually given
            print("years from the Synod of Whitby of 664 to the Kogo register of 670:", KOGO - WHITBY)
        """,
    ),

)
