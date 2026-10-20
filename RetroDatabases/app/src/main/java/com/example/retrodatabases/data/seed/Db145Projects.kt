package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 590 to 600: the reigns of Reccared, Authari and Agilulf, three rulers who held power in 590, counted and ranked by a short program. */
val db145Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Three rulers who held power in 590, Reccared, Authari and Agilulf, sorted by the length of their reigns",
        originalTitle = "Reccared, Authari and Agilulf",
        author = "Compilers of the royal lists",
        year = 590,
        summary = "The reigns of Reccared, Authari and Agilulf are counted in years, summed and ranked by a short program, and the longest of the three is Agilulf's.",
        details = "Reccared ruled the Visigoths from 586 to 601, Authari the Lombards from 584 to 590, and Agilulf the Lombards from 590 to 616. All three held power in 590, which is the year of the title and not a date of the project, and the lengths depend on the accession and death years the usual lists give.",
        instructions = """
            1. Save the example as ruler_reigns.py.
            2. Run it: python3 ruler_reigns.py
        """,
        example = """
            REIGNS = [
                ("Reccared", 586, 601),
                ("Authari", 584, 590),
                ("Agilulf", 590, 616),
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
