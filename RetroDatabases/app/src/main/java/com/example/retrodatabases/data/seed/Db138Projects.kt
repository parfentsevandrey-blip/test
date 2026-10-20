package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 660 to 670: the reigns of Heraclius, Constans II and Constantine IV from 610 to 685, counted and ranked by a short program. */
val db138Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.PROJECT,
        section = "Byzantine",
        title = "The reigns of Heraclius, Constans II and Constantine IV, from 610 to 685, counted and ranked by a short program",
        originalTitle = "Heraclius, Constans II and Constantine IV",
        author = "The Byzantine emperors Heraclius, Constans II and Constantine IV",
        year = 668,
        summary = "The reigns of Heraclius, Constans II and Constantine IV are counted from their years of accession, then summed and ranked by a short program.",
        details = "Heraclius ruled from 610 to 641, Constans II from 641 until his death at Syracuse in 668, and Constantine IV from 668 to 685, so the spans are counted in whole years. The figures are approximate because the sources give years and not exact days.",
        rare = true,
        instructions = """
            1. Save the example as byzantine_reigns.py.
            2. Run it: python3 byzantine_reigns.py
        """,
        example = """
            REIGNS = [
                ("Heraclius", 610, 641),
                ("Constans II", 641, 668),
                ("Constantine IV", 668, 685),
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
