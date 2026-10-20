package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 344: Aphrahat's twenty-three Demonstrations, counted by the years the sources give for their writing. */
val db170Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.EXAMPLE,
        section = "Texts",
        title = "Aphrahat's twenty-three Demonstrations, counted by the years in which they were written",
        originalTitle = "Demonstrations",
        author = "Aphrahat, the Persian Sage",
        year = 344,
        summary = "Aphrahat's twenty-three Demonstrations, Syriac treatises written in the Persian empire, fall into groups dated 337, 344 and 345, and a short program counts them by year.",
        details = "The sources date the first ten Demonstrations to 337 and the next twelve, numbers 11 to 22, to 344, with the last treatise dated to 345 by some scholars. Other accounts count only twenty-two treatises, so the total is itself a point of counting, and the example uses the groups as the sources give them.",
        instructions = """
            1. Save the example as demonstrations.py.
            2. Run it: python3 demonstrations.py
        """,
        example = """
            # written[year] = number of Demonstrations dated to that year, as the sources group them
            written = {337: 10, 344: 12, 345: 1}
            for year in sorted(written):
                print(year, written[year])
            print("total", sum(written.values()))
            print("written from 340 to 350:", sum(n for year, n in written.items() if 340 <= year <= 350))
        """,
        rare = true,
    ),

)
