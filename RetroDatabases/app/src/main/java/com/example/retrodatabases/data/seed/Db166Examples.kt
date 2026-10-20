package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 380 to 390: the seven canons of the council of Constantinople, counted in code. */
val db166Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.EXAMPLE,
        section = "Councils",
        title = "The seven canons of the council of Constantinople in 381, counted in code",
        originalTitle = "Canones",
        author = "The bishops of the council of 381",
        year = 381,
        summary = "The canons of the council of Constantinople in 381 are traditionally counted as seven, and a short program counts them and adds up their numbers.",
        details = "The canons are numbered one to seven, so the example counts them and adds the numbers to 28. Scholars debate how closely the seven texts reflect the council's own decisions.",
        instructions = """
            1. Save the example as canons.py.
            2. Run it: python3 canons.py
        """,
        example = """
            CANONS = list(range(1, 8))
            print("canons:", len(CANONS))
            print("sum of the canon numbers:", sum(CANONS))
        """,
    ),

)
