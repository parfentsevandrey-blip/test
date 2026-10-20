package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 690 to 700: the first reign of Justinian II, the reign of Leontius and the reign of Tiberius III, from 685 to 705, summed and ranked by a short program. */
val db135Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.PROJECT,
        section = "Byzantine",
        title = "Justinian II's first reign, from 685 to 695, and the reigns of Leontius and Tiberius III to 705, summed and ranked by a short program",
        originalTitle = "Iustinianus II, Leontius, Tiberius III",
        author = "Byzantine emperors",
        year = 695,
        summary = "Justinian II's first reign from 685 to 695, the reign of Leontius from 695 to 698 and the reign of Tiberius III from 698 to 705 are summed and ranked by a short program.",
        details = "Justinian II lost the throne in 695 to Leontius, who was overthrown by Tiberius III in 698, and Justinian returned only in 705, so the lengths are counted from years of accession and not from exact days. The program prints each length, the total and the longest and shortest reign, which come to 20 years in all.",
        rare = true,
        instructions = """
            1. Save the example as byzantine_reigns.py.
            2. Run it: python3 byzantine_reigns.py
        """,
        example = """
            REIGNS = [
                ("Justinian II, first reign", 685, 695),
                ("Leontius", 695, 698),
                ("Tiberius III", 698, 705),
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
