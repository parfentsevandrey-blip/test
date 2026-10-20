package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 10 to 20: the years from Augustus's Res Gestae to the fall of Wang Mang, computed in one line. */
val db203Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Augustus's Res Gestae to the fall of Wang Mang",
        originalTitle = "Years between two dates",
        author = "No single author",
        year = 14,
        summary = "The Res Gestae is dated to 14 and Wang Mang fell in 23, and a one-line program finds that nine years lie between the two dates.",
        details = "Both years are as the records give them: 14 for the first public form of the Res Gestae and 23 for the fall of Wang Mang. The example prints the difference, which can be checked by hand from the two years.",
        instructions = """
            1. Save the example as years_gap.py.
            2. Run it: python3 years_gap.py
        """,
        example = """
            res_gestae = 14       # year of the first public form of the Res Gestae
            wang_mang_fall = 23   # year Wang Mang fell
            print("years from the Res Gestae to the fall of Wang Mang:", wang_mang_fall - res_gestae)
        """,
        rare = true,
    ),

)
