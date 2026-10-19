package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 700 to 710: the reigns of Empress Wu, Zhongzong and Ruizong from 690 to 712, summed and ranked by a short program. */
val db134Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.PROJECT,
        section = "Chinese",
        title = "The reigns of Empress Wu, Zhongzong and Ruizong from 690 to 712, summed and ranked by a short program",
        originalTitle = "Wu Zetian, Zhongzong and Ruizong",
        author = "Empress Wu and her successors",
        year = 705,
        summary = "Empress Wu's reign from 690 to 705, Zhongzong's second reign from 705 to 710 and Ruizong's second reign from 710 to 712 are summed and ranked by a short program.",
        details = "Empress Wu proclaimed her Zhou dynasty in 690 and gave up the throne in 705, when Zhongzong was restored. Zhongzong died in 710, and Ruizong came to the throne again, so the lengths are counted from years of accession and not from exact days.",
        rare = true,
        instructions = """
            1. Save the example as reign_lengths.py.
            2. Run it: python3 reign_lengths.py
        """,
        example = """
            REIGNS = [
                ("Empress Wu as sovereign", 690, 705),
                ("Zhongzong's second reign", 705, 710),
                ("Ruizong's second reign", 710, 712),
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
