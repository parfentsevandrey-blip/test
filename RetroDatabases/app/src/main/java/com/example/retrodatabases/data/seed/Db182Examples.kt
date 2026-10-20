package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 220 to 230: a count of the years of Sasanian rule, from two starts to the end in 651, computed in code. */
val db182Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.EXAMPLE,
        section = "Dynasties",
        title = "Sasanian rule counted from the battle of 224 and from the crowning of 226 to 651",
        originalTitle = "Sasanian rule counted from the battle of 224 and from the crowning of 226 to 651",
        author = "No single author",
        year = 224,
        summary = "Two starts for Sasanian rule, the battle of 224 and the crowning of 226, give 427 and 425 years to the end in 651, and a short program counts both.",
        details = "The end in 651 is the usual date for the close of the dynasty, when its last king Yazdegerd III died, and the two starts reflect the dispute over where Sasanian rule began. The count is a span of years, not a count of reigns, and it stays the same on every run.",
        instructions = """
            1. Save the example as sasanian_years.py.
            2. Run it: python3 sasanian_years.py
        """,
        example = """
            # Sasanian rule counted from two starts to the end in 651.
            END_YEAR = 651
            for label, start in [("the battle of 224", 224), ("the crowning of 226", 226)]:
                print(f"from {label} to 651: {END_YEAR - start} years")
            print("difference between the starts:", 226 - 224)
        """,
    ),

)
