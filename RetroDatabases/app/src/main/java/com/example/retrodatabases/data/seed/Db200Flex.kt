package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 40 to 50: the years from Pomponius Mela's Chorographia to Claudius's census. */
val db200Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Pomponius Mela's Chorographia to Claudius's census",
        originalTitle = "Years between two dated items",
        author = "Pomponius Mela and Claudius",
        year = 47,
        summary = "Mela's Chorographia is dated about 43 and Claudius's census begins in 47, and a short line of arithmetic gives the four years between them.",
        details = "Both dates are estimates or dated by some accounts: Mela's date is inferred, and the census is given as 47 by some accounts and 48 by others. The example prints the difference from the two years as given, so the four years hold only for those dates.",
        instructions = """
            1. Save the example as mela_census.py.
            2. Run it: python3 mela_census.py
        """,
        example = """
            MELA = 43       # Pomponius Mela's Chorographia, about 43 as usually given
            CENSUS = 47     # Claudius's census, begun in 47 by some accounts
            print("years from Mela to the census:", CENSUS - MELA)
        """,
        rare = true,
    ),

)
