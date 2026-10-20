package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 280 to 290: the years from Xun Xu's catalogue, dated about 280, to Wani's traditional arrival in 285. */
val db176Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Xun Xu's catalogue to Wani's traditional arrival in Japan",
        originalTitle = "Years between two dated events",
        author = "No single author",
        year = 280,
        summary = "Xun Xu's catalogue is dated about 280 and Wani's traditional arrival to 285, and a short program counts the years between them.",
        details = "The five years are an approximate span, because the catalogue is dated only as about 280, while Wani's year is the traditional year of his arrival. The example prints the count from two fixed years, so the result can be checked by hand.",
        instructions = """
            1. Save the example as catalogue_wani.py.
            2. Run it: python3 catalogue_wani.py
        """,
        example = """
            catalogue = 280   # Xun Xu's catalogue, dated about 280
            wani = 285        # Wani's arrival, the traditional year
            print("years from the catalogue to Wani:", wani - catalogue)
        """,
        rare = true,
    ),

)
