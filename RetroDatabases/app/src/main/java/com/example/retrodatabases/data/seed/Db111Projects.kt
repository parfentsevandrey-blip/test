package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A project of 936: the reign lengths of three rulers of Cordoba, summed and ranked by a short program. */
val db111Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.PROJECT,
        section = "Spain",
        title = "Reign lengths of three Cordoba rulers, summed and ranked",
        originalTitle = "Reign lengths of three Cordoba rulers, summed and ranked",
        author = "Abd al-Rahman II, Muhammad I and Abd al-Rahman III",
        year = 936,
        summary = "The reigns of three rulers of Cordoba, from 822 to 961, are summed and ranked by a short program, which finds the longest and the shortest reign.",
        details = "The three reigns are given by their usual dates: Abd al-Rahman II from 822 to 852, Muhammad I from 852 to 886 and Abd al-Rahman III from 912 to 961. The year 936 is the year in which the palace city of Madinat al-Zahra is usually dated, in the reign of the last of these rulers, and the run tests the arithmetic rather than the events.",
        rare = true,
        instructions = """
            1. Save the example as reigns.py.
            2. Run it: python3 reigns.py
        """,
        example = """
            rulers = [
                ("Abd al-Rahman II", 822, 852),
                ("Muhammad I", 852, 886),
                ("Abd al-Rahman III", 912, 961),
            ]
            lengths = [(name, end - start) for name, start, end in rulers]
            print("rulers:", len(lengths))
            print("total years:", sum(length for _, length in lengths))
            longest = max(lengths, key=lambda item: item[1])
            shortest = min(lengths, key=lambda item: item[1])
            print("longest:", longest[0], longest[1], "years")
            print("shortest:", shortest[0], shortest[1], "years")
            for name, length in sorted(lengths, key=lambda item: item[1]):
                print(name, length)
        """,
    ),

)
