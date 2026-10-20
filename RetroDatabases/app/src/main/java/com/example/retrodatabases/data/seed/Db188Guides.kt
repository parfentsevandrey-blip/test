package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 160 to 170: how a count is written as tally marks in fives, placed at 165, the year the Antonine plague is said to begin. */
val db188Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.GUIDE,
        section = "Counting",
        title = "How a count is written as tally marks in fives",
        originalTitle = "Tally marks in fives",
        author = "No single author",
        year = 165,
        summary = "A count is written as tally marks in fives, four strokes and a crossing stroke for each five, and a short program writes four counts this way.",
        details = "The sources do not date this method; 165 is the year of the anchor, the year the Antonine plague is said to begin, not of the method. The example writes the counts 5, 7, 13 and 20, and each five is marked with four upright strokes and one crossing stroke.",
        rare = true,
        instructions = """
            1. Save the example as tally.py.
            2. Run it: python3 tally.py
        """,
        example = """
            def tally_marks(count):
                groups = []
                while count >= 5:
                    groups.append("||||/")
                    count -= 5
                if count > 0:
                    groups.append("|" * count)
                return " ".join(groups)

            for count in [5, 7, 13, 20]:
                print(count, tally_marks(count))
        """,
    ),

)
