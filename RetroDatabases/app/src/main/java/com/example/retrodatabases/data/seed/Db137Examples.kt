package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 670 to 680: the bishops, deacons and abbots of the Eleventh Council of Toledo, counted in code. */
val db137Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.EXAMPLE,
        section = "Visigothic",
        title = "The bishops, deacons and abbots at the Eleventh Council of Toledo of 675, counted in code",
        originalTitle = "Concilium Toletanum XI",
        author = "The Eleventh Council of Toledo",
        year = 675,
        summary = "A short program totals the bishops, deacons and abbots that accounts give for the Eleventh Council of Toledo of 675.",
        details = "Accounts give seventeen bishops, two deacons and five abbots at the council, so the program prints each rank and a total of twenty-four members. The numbers are the commonly given attendance, and the program only adds them, so it checks the arithmetic and not the list itself.",
        rare = true,
        instructions = """
            1. Save the example as toledo_members.py.
            2. Run it: python3 toledo_members.py
        """,
        example = """
            BISHOPS = 17  # bishops at the council, as commonly given
            DEACONS = 2   # deacons, as commonly given
            ABBOTS = 5    # abbots, as commonly given
            print("bishops:", BISHOPS)
            print("deacons:", DEACONS)
            print("abbots:", ABBOTS)
            print("total members:", BISHOPS + DEACONS + ABBOTS)
        """,
    ),

)
