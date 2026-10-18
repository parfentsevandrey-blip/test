package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A project of 997: five reigns that overlap the 990s, summed and ranked by length. */
val db105Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Reigns of five rulers who held power in the 990s, summed and ranked",
        originalTitle = "Reigns of five rulers who held power in the 990s, summed and ranked",
        author = "Basil II, Song Taizong, Song Zhenzong, Hugh Capet and Robert II",
        year = 997,
        summary = "Five reigns that overlap the 990s add up to 139 years, and ranked by length Basil II's 49 years come first and Hugh Capet's 9 years come last.",
        details = "The list is a compilation and has no date of its own, so 997 is a placed year: it is the year Song Zhenzong succeeded Song Taizong, one of the reigns in the list. The reign years are taken as commonly stated, and each length is the difference between the end year and the start year.",
        instructions = """
            1. Save the example as reigns_90s.py.
            2. Run it: python3 reigns_90s.py
        """,
        example = """
            # Reigns of five rulers who held power in the 990s, in years as commonly stated.
            reigns = [
                ("Basil II", 976, 1025),
                ("Song Taizong", 976, 997),
                ("Song Zhenzong", 997, 1022),
                ("Hugh Capet", 987, 996),
                ("Robert II", 996, 1031),
            ]

            lengths = [(name, end - start) for name, start, end in reigns]
            ranked = sorted(lengths, key=lambda item: item[1], reverse=True)
            total = sum(years for _, years in lengths)

            print("rulers:", len(lengths))
            print("total years:", total)
            print(f"longest: {ranked[0][0]} {ranked[0][1]} years")
            print(f"shortest: {ranked[-1][0]} {ranked[-1][1]} years")
            print("ranked:")
            for name, years in ranked:
                print(f"{name} {years}")
        """,
    ),

)
