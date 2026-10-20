package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 680 to 690: how a Saka date on the Old Malay stones becomes a Common Era year, worked out by a program. */
val db136Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.GUIDE,
        section = "Old Malay",
        title = "How a Saka date on the Old Malay stones of 682 and 686 becomes a Common Era year, worked out in code",
        originalTitle = "Saka era",
        author = "The Saka era reckoning",
        year = 686,
        summary = "A Saka year on an Old Malay stone becomes a Common Era year by adding 78, and a short program applies the rule to the Kedukan Bukit and Kota Kapur dates.",
        details = "The Saka era begins in 78 CE, so adding 78 gives the Common Era year, but near the start of a Saka year the month decides which year a date falls in. The program prints the Kedukan Bukit stone in both of its readings, 604 and 605 Saka, and the Kota Kapur stone at 608 Saka.",
        rare = true,
        instructions = """
            1. Save the example as saka_stones.py.
            2. Run it: python3 saka_stones.py
        """,
        example = """
            SAKA_TO_CE = 78  # the Saka era begins in 78 CE, so CE = Saka + 78
            STONES = [
                ("Kedukan Bukit stone", 604),
                ("Kedukan Bukit stone, other reading", 605),
                ("Kota Kapur stone", 608),
            ]
            for name, saka in STONES:
                print(name + ": Saka", saka, "is", saka + SAKA_TO_CE, "CE")
        """,
    ),

)
