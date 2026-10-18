package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 910 to 920: a method for filing events under their years, as annals do, and printing the years in order. */
val db113Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.GUIDE,
        section = "Methods",
        title = "How annals file each event under its year and print the years in order",
        originalTitle = "How annals file each event under its year and print the years in order",
        author = "Medieval annalists",
        year = 915,
        summary = "An annal files each event under its year, so a program can group the events by year and print the years in their order.",
        details = "Annals were a common form of medieval chronicle, with the events of each year set under that year, and Tabari's history, finished about 915, is arranged this way. The program files four events of the decade under their years and prints each year with the events filed under it.",
        rare = true,
        instructions = """
            1. Save the example as annals.py.
            2. Run it: python3 annals.py
        """,
        example = """
            # annals: each event is filed under its year, and the years are printed in order
            events = [
                (918, "Goryeo founded at Songak"),
                (910, "Fatimid caliphate proclaimed in Ifriqiya"),
                (917, "Southern Han set up at Guangzhou"),
                (910, "Cluny charter issued"),
            ]
            annals = {}
            for year, event in events:
                annals.setdefault(year, []).append(event)
            for year in sorted(annals):
                print(year, "|", "; ".join(annals[year]))
        """,
    ),

)
