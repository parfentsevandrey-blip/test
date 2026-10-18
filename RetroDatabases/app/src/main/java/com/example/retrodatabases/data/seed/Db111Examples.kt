package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 930 and 938: the years of the Icelandic Commonwealth counted in code, and the five capitals of the Liao sorted by name. */
val db111Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.EXAMPLE,
        section = "Iceland",
        title = "Years of the Icelandic Commonwealth from 930 to 1262, counted in code",
        originalTitle = "Years of the Icelandic Commonwealth from 930 to 1262, counted in code",
        author = "The chieftains of the Althing",
        year = 930,
        summary = "The Icelandic Commonwealth runs from 930 to 1262, a span of 332 years, which a short program counts and splits into whole centuries and the years left over.",
        details = "The Commonwealth is dated 930 to 1262 in the usual reference works, and the start year is the traditional one given for the Althing. The program only counts the span and its parts, so the run tests the arithmetic rather than the events, and the years are given as commonly stated.",
        instructions = """
            1. Save the example as commonwealth.py.
            2. Run it: python3 commonwealth.py
        """,
        example = """
            start = 930
            end = 1262
            span = end - start
            print("years of the Commonwealth:", span)
            print("whole centuries:", span // 100)
            print("years left over:", span % 100)
            print("years from 930 to 1000:", 1000 - start)
        """,
    ),

    entry(
        databaseId = 111,
        kind = Kind.EXAMPLE,
        section = "China",
        title = "The five capitals of the Liao, sorted by name in code",
        originalTitle = "The five capitals of the Liao, sorted by name in code",
        author = "Taizong of Liao",
        year = 938,
        summary = "The five capitals of the Liao state, the last of them Nanjing made in 938, are listed and sorted by a short program that finds where Nanjing falls in the list.",
        details = "The five capitals were Shangjing, Zhongjing, Dongjing, Xijing and Nanjing, listed here in the order given by the sources with Nanjing last. The sorting is alphabetical and carries no claim about the rank of the capitals, on which accounts differ.",
        instructions = """
            1. Save the example as liao_capitals.py.
            2. Run it: python3 liao_capitals.py
        """,
        example = """
            capitals = ["Shangjing", "Zhongjing", "Dongjing", "Xijing", "Nanjing"]
            print("capitals:", len(capitals))
            print("sorted:", ", ".join(sorted(capitals)))
            print("position of Nanjing in the list:", capitals.index("Nanjing") + 1)
            print("last capital in the list:", capitals[-1])
        """,
    ),

)
