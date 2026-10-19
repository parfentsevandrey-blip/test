package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 770 to 780: four rulers in office in 775, their years in power summed, sorted and ranked by a short program. */
val db127Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.PROJECT,
        section = "Reigns",
        title = "Four rulers in office in 775, their years in power summed, sorted and ranked",
        originalTitle = "Four rulers of 775",
        author = "Constantine V, al-Mansur, al-Mahdi and Leo IV",
        year = 775,
        summary = "Four rulers held power in 775, two of them dying that year and two coming to power, and a short program sums and ranks their years in office.",
        details = "Constantine V is usually counted from his accession in 741 to his death in 775, although the usurper Artabasdos held Constantinople in 742 and 743; al-Mansur reigned from 754 to 775, al-Mahdi from 775 to 785 and Leo IV from 775 to 780. The lengths of 34, 21, 10 and 5 years add up to 70, and the program prints them in order so that each subtraction can be checked.",
        instructions = """
            1. Save the example as reigns_775.py.
            2. Run it: python3 reigns_775.py
        """,
        example = """
            RULERS = [
                ("Constantine V of Byzantium", 741, 775),
                ("al-Mansur of Baghdad", 754, 775),
                ("al-Mahdi of Baghdad", 775, 785),
                ("Leo IV of Byzantium", 775, 780),
            ]

            lengths = [(end - start, name) for name, start, end in RULERS]
            for years, name in sorted(lengths, reverse=True):
                print(name + ":", years, "years")
            print("total:", sum(years for years, _ in lengths), "years")
            print("longest:", max(lengths)[1])
            print("shortest:", min(lengths)[1])
        """,
    ),

)
