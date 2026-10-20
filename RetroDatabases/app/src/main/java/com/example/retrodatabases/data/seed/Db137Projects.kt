package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 670 to 680: the reigns of Ecgfrith, Tenmu and Wamba from 670 to 686, summed and ranked by a short program. */
val db137Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "The reigns of Ecgfrith, Tenmu and Wamba, from 670 to 686, summed and ranked by a short program",
        originalTitle = "Reign lengths",
        author = "Ecgfrith, Tenmu and Wamba",
        year = 670,
        summary = "The reigns of Ecgfrith of Northumbria, Tenmu of Japan and Wamba of the Visigoths are summed and ranked by a short program.",
        details = "Ecgfrith's reign is counted from 670 to 685, Tenmu's from 673 to 686 and Wamba's from 672 to 680, so the lengths are 15, 13 and 8 years. The counts use the years of accession and of death as commonly given, not exact days, and the program prints the total and the longest and shortest reign.",
        rare = true,
        instructions = """
            1. Save the example as reign_ranks.py.
            2. Run it: python3 reign_ranks.py
        """,
        example = """
            REIGNS = [
                ("Ecgfrith of Northumbria", 670, 685),
                ("Tenmu of Japan", 673, 686),
                ("Wamba of the Visigoths", 672, 680),
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
