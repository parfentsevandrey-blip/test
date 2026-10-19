package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 847: the years in power of three Abbasid caliphs, from al-Mu'tasim to al-Mutawakkil, summed and ranked by a short program. */
val db120Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.PROJECT,
        section = "Abbasid",
        title = "Abbasid caliphs from al-Mu'tasim to al-Mutawakkil, their years in power summed and ranked",
        originalTitle = "Abbasid caliphs from al-Mu'tasim to al-Mutawakkil, their years in power summed and ranked",
        author = "The caliphs al-Mu'tasim, al-Wathiq and al-Mutawakkil",
        year = 847,
        summary = "Three Abbasid caliphs, al-Mu'tasim, al-Wathiq and al-Mutawakkil, held power from 833 to 861, and a short program sums their years and ranks them.",
        details = "The reigns used here are 833 to 842 for al-Mu'tasim, 842 to 847 for al-Wathiq and 847 to 861 for al-Mutawakkil, which are the usual dates. The year 847 is the accession of al-Mutawakkil, and the program prints the total years in power and the three caliphs in order of length.",
        instructions = """
            1. Save the example as caliphs.py.
            2. Run it: python3 caliphs.py
        """,
        example = """
            CALIPHS = [
                ("al-Mu'tasim", 833, 842),
                ("al-Wathiq", 842, 847),
                ("al-Mutawakkil", 847, 861),
            ]

            lengths = [(name, end - start) for name, start, end in CALIPHS]
            print("caliphs:", len(lengths))
            print("total years in power:", sum(years for _, years in lengths))
            for name, years in sorted(lengths, key=lambda item: item[1], reverse=True):
                print(name, years, "years")
        """,
    ),

)
