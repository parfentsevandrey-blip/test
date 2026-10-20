package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 200 to 210: the four ages of the Manusmriti added up in divine years and turned into human years. */
val db184Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.EXAMPLE,
        section = "Time",
        title = "The four ages of the Manusmriti added up in divine and human years",
        originalTitle = "Yugas in the Manava Dharmasastra",
        author = "Attributed to Manu",
        year = 200,
        summary = "The four ages in the Manusmriti last 12,000 divine years in all, and a short program adds them and turns the total into human years.",
        details = "The text gives the four ages as 4,800, 3,600, 2,400 and 1,200 divine years, each with its dawn and dusk, and a divine year as 360 human years, so the total is 4,320,000 human years. The example uses only these figures and prints each age, the divine total and the human total so the sums can be checked by hand.",
        instructions = """
            1. Save the example as ages.py.
            2. Run it: python3 ages.py
        """,
        example = """
            ages = [
                ("Krita", 4000, 400, 400),
                ("Treta", 3000, 300, 300),
                ("Dvapara", 2000, 200, 200),
                ("Kali", 1000, 100, 100),
            ]
            DIVINE_YEAR = 360   # one year of the gods, in years of men
            total = 0
            for name, years, dawn, dusk in ages:
                span = years + dawn + dusk
                total += span
                print(name, span)
            print("divine years in all:", total)
            print("human years in all:", total * DIVINE_YEAR)
        """,
        rare = true,
    ),

)
