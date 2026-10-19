package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 850 to 860: the saltpetre formulas in the Zhenyuan miaodao yaolue, counted and turned into a share in code. */
val db119Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.EXAMPLE,
        section = "Alchemy",
        title = "Share of saltpetre formulas among the 34 elixirs of the Zhenyuan miaodao yaolue",
        originalTitle = "Share of saltpetre formulas",
        author = "Anonymous Taoist alchemists",
        year = 858,
        summary = "The Zhenyuan miaodao yaolue is said to hold 34 formulas for hazardous elixirs, and a short program turns the three that use saltpetre into a share of 8.82 percent.",
        details = "Saltpetre appears in three of the 34 formulas, and the program divides the count to print the share as a percentage. The year 858 is the date one account gives for the passage, and estimates for the text run from the mid-ninth century to about 950.",
        instructions = """
            1. Save the example as saltpetre_share.py.
            2. Run it: python3 saltpetre_share.py
        """,
        example = """
            FORMULAS = 34          # formulas for hazardous elixirs, as one account counts them
            WITH_SALTPETRE = 3     # formulas in which saltpetre appears
            share = WITH_SALTPETRE * 100 / FORMULAS
            print("formulas:", FORMULAS)
            print("with saltpetre:", WITH_SALTPETRE)
            print("share: %.2f percent" % share)
        """,
    ),

)
