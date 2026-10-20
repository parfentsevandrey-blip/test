package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 610 to 620: the reigns of Empress Suiko, Sui Yangdi and Tang Gaozu, counted and sorted by a short program. */
val db143Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.PROJECT,
        section = "East Asian",
        title = "Spans of three East Asian rulers, from 593 to 628, counted and sorted in a short program",
        originalTitle = "Suiko tenno, Yangdi and Gaozu",
        author = "Empress Suiko, Yang Di and Li Yuan",
        year = 618,
        summary = "The reigns of Empress Suiko, Sui Yangdi and Tang Gaozu are counted from accession to end, and a short program sorts them by length.",
        details = "Suiko's reign runs from 593 to 628, Yangdi's from 604 to 618 and Gaozu's from 618 to 626, and the lengths are counted in whole years from the years of accession and not from exact days. The three reigns belong to different states, so the program compares only their lengths.",
        rare = true,
        instructions = """
            1. Save the example as east_reigns.py.
            2. Run it: python3 east_reigns.py
        """,
        example = """
            REIGNS = [
                ("Empress Suiko of Japan", 593, 628),
                ("Sui Yangdi", 604, 618),
                ("Tang Gaozu", 618, 626),
            ]

            lengths = {name: end - start for name, start, end in REIGNS}
            for name, span in lengths.items():
                print(name, span, "years")
            print("total:", sum(lengths.values()), "years")
            longest = max(lengths, key=lengths.get)
            shortest = min(lengths, key=lengths.get)
            print("longest:", longest, lengths[longest], "years")
            print("shortest:", shortest, lengths[shortest], "years")
        """,
    ),

)
