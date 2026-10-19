package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 790 to 800: the reigns of four rulers who held power in 795, summed and ranked by a short program. */
val db125Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Reign lengths of four rulers who held power in 795, summed and ranked",
        originalTitle = "Reign lengths in 795",
        author = "Offa of Mercia, Alfonso II, Dezong and Kanmu",
        year = 795,
        summary = "Four rulers in power in 795 reigned 141 years between them, and a short program prints each reign, the total, and the longest and shortest reign.",
        details = "Offa's reign ran from 757 to his death in 796, Alfonso II's from 791 to 842, Dezong's from 779 to 805, and Kanmu's from 781 to 806. The sums are arithmetic on these usual accession and death years, and they make no claim about how the four reigns were linked.",
        instructions = """
            1. Save the example as reign_lengths.py.
            2. Run it: python3 reign_lengths.py
        """,
        example = """
            REIGNS = [
                ("Offa of Mercia", 757, 796),
                ("Alfonso II of Asturias", 791, 842),
                ("Dezong of Tang China", 779, 805),
                ("Kanmu of Japan", 781, 806),
            ]
            lengths = []
            for name, start, end in REIGNS:
                lengths.append((end - start, name))
                print(name + ":", end - start, "years")
            print("total:", sum(length for length, _ in lengths), "years")
            ranked = sorted(lengths, reverse=True)
            print("longest:", ranked[0][1] + ",", ranked[0][0], "years")
            print("shortest:", ranked[-1][1] + ",", ranked[-1][0], "years")
        """,
    ),

)
