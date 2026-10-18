package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One line of arithmetic: the years from the gwageo of 958 to its abolition in 1894. */
val db109Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the gwageo of 958 to its abolition in 1894",
        originalTitle = "Years from 958 to 1894",
        author = "King Gwangjong of Goryeo and the Gabo reformers",
        year = 958,
        summary = "One line of arithmetic counts the years from the setting up of the gwageo in 958 to its abolition in 1894, which makes 936 years.",
        details = "Both dates are given as commonly stated, and the example uses whole years only, so the count tests arithmetic rather than the history of the examination. The abolition came in the Gabo reforms of 1894, which did away with the old examination system.",
        instructions = """
            1. Save the example as gwageo_years.py.
            2. Run it: python3 gwageo_years.py
        """,
        example = """
            print("years from 958 to 1894:", 1894 - 958)
        """,
    ),

)
