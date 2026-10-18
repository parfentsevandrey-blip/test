package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1098 to 1132: the first daughter houses of Citeaux and the years of their foundation. */
val db91Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.PROJECT,
        section = "Orders",
        title = "The first daughter houses of Citeaux and the years they were founded",
        originalTitle = "Filiation of Citeaux",
        author = "The Cistercian order",
        year = 1132,
        summary = "Citeaux's first four daughter houses were founded between 1113 and 1115, and a short program measures each house's distance in years from Citeaux and from the foundation of Rievaulx in 1132.",
        details = "The four daughters were La Ferte, Pontigny, Clairvaux and Morimond, and Citeaux itself dates from 1098. The years are fixed, so the program prints the gap from the mother house for each daughter and the total of the four gaps, which can be checked by hand.",
        instructions = """
            1. Save the example as citeaux.py.
            2. Run it: python3 citeaux.py
        """,
        example = """
            MOTHER = ("Citeaux", 1098)
            DAUGHTERS = [("La Ferte", 1113), ("Pontigny", 1114), ("Clairvaux", 1115), ("Morimond", 1115)]
            for name, founded in DAUGHTERS:
                print(f"{name}: {founded - MOTHER[1]} years after Citeaux")
            print("Rievaulx, 1132:", 1132 - MOTHER[1], "years after Citeaux")
            print("total of the four gaps:", sum(founded - MOTHER[1] for _, founded in DAUGHTERS))
        """,
        rare = true,
    ),

)
