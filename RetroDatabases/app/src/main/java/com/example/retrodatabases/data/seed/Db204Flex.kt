package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 0 to 10: the years from the Han census of 2 to the defeat of Varus in 9. */
val db204Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Han census of 2 to the defeat of Varus in 9",
        originalTitle = "Years between two dated events",
        author = "No single author",
        year = 2,
        summary = "The Han census of 2 and the defeat of Varus in 9 are seven years apart, and a one-line program counts the gap.",
        details = "Both years are given as commonly stated, with the census year taken from the Yuanshi dating of the Hanshu and the defeat in the usual account of the year 9. The count is simple arithmetic, so the example checks only the subtraction and not the dates themselves.",
        instructions = """
            1. Save the example as census_varus.py.
            2. Run it: python3 census_varus.py
        """,
        example = """
            census = 2      # the Han census, Yuanshi 2
            varus = 9       # the defeat of Varus, as usually dated
            print("years from the census to the defeat of Varus:", varus - census)
        """,
    ),

)
