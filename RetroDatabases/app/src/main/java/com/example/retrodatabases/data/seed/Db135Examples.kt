package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 690 to 700: the 102 canons of the Quinisext Council and the 85 Apostolic canons it accepted, added up and split into blocks of ten in code. */
val db135Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.EXAMPLE,
        section = "Byzantine",
        title = "The 102 canons of the Quinisext Council and the 85 Apostolic canons it accepted, counted in code",
        originalTitle = "Canones Quinisexti",
        author = "Justinian II and the bishops of Constantinople",
        year = 692,
        summary = "The 102 canons of the Quinisext Council and the 85 Apostolic canons that it accepted are added up and split into blocks of ten by a short program.",
        details = "Canon 2 of the council accepted the 85 Apostolic canons, which were collected under the name of the apostles, and the council's own canons number 102 in the usual count. The program prints the two counts, their total and the blocks of ten, which is simple arithmetic on the figures as they are usually given.",
        instructions = """
            1. Save the example as quinisext_canons.py.
            2. Run it: python3 quinisext_canons.py
        """,
        example = """
            QUINISEXT_CANONS = 102  # the canons of the Quinisext Council, in the usual count
            APOSTOLIC_CANONS = 85   # the Apostolic canons that the council accepted
            print("canons of the Quinisext Council:", QUINISEXT_CANONS)
            print("Apostolic canons accepted by it:", APOSTOLIC_CANONS)
            print("both sets together:", QUINISEXT_CANONS + APOSTOLIC_CANONS)
            print("full blocks of ten:", QUINISEXT_CANONS // 10, "left over:", QUINISEXT_CANONS % 10)
        """,
    ),

)
