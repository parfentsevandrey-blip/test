package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 800 to 810: the Japanese capitals from 710 to 810, the years each one served, summed and ranked by a short program. */
val db124Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.PROJECT,
        section = "Japanese",
        title = "Japan's capitals from 710 to 810, the years each one served, summed and ranked",
        originalTitle = "Heijo-kyo, Nagaoka-kyo and Heian-kyo",
        author = "The Japanese court",
        year = 810,
        summary = "The Japanese capitals of Heijo-kyo at Nara, Nagaoka-kyo and Heian-kyo, counted from 710 to 810, held the court for 74, 10 and 16 years, which a short program sums and ranks.",
        details = "The court moved to Nagaoka in 784 and to Heian-kyo in 794, and in 810 Emperor Heizei tried to return to Nara, so the count stops there. The brief moves of the court between 740 and 745 are left out, and Heian-kyo is counted only up to 810.",
        rare = true,
        instructions = """
            1. Save the example as japan_capitals.py.
            2. Run it: python3 japan_capitals.py
        """,
        example = """
            CAPITALS = [
                ("Heijo-kyo at Nara", 710, 784),
                ("Nagaoka-kyo", 784, 794),
                ("Heian-kyo", 794, 810),
            ]

            years = {name: end - start for name, start, end in CAPITALS}
            for name, span in years.items():
                print(name, span, "years")
            print("total:", sum(years.values()), "years")
            longest = max(years, key=years.get)
            shortest = min(years, key=years.get)
            print("longest:", longest, years[longest], "years")
            print("shortest:", shortest, years[shortest], "years")
        """,
    ),

)
