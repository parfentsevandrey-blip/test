package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 600 to 610: the reigns of the Sui emperors Wen and Yang, counted and ranked by a short program. */
val db144Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.PROJECT,
        section = "Chinese",
        title = "The Sui emperors Wen and Yang, whose two reigns from 581 to 618 are counted and ranked in code",
        originalTitle = "Sui Wendi and Sui Yangdi",
        author = "Compilers of the Sui shu",
        year = 604,
        summary = "The reigns of Emperor Wen, 581 to 604, and Emperor Yang, 604 to 618, are counted in years, summed and ranked by a short program.",
        details = "The year 604 is the handover between the two reigns and an anchor for this record, not a date of the project, and the two reigns together make up the Sui dynasty from 581 to 618. The program counts 23 years for Emperor Wen and 14 for Emperor Yang, 37 in all, and names the longer reign, and some tables give the end of Yang's reign as 617, which would change the second figure.",
        instructions = """
            1. Save the example as sui_reigns.py.
            2. Run it: python3 sui_reigns.py
        """,
        example = """
            REIGNS = [
                ("Emperor Wen", 581, 604),
                ("Emperor Yang", 604, 618),
            ]

            lengths = [(name, end - start) for name, start, end in REIGNS]
            for name, years in lengths:
                print(name, years, "years")
            print("total:", sum(years for _, years in lengths), "years")
            longest = max(lengths, key=lambda item: item[1])
            print("longest:", longest[0], longest[1], "years")
        """,
    ),

)
