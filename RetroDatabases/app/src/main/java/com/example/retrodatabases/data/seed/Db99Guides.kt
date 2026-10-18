package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1050: Jia Xian's table of coefficients for extracting roots, built one row at a time and printed by a short program. */
val db99Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.GUIDE,
        section = "Roots",
        title = "How Jia Xian's table of coefficients for extracting roots is built, one row at a time",
        originalTitle = "Jia Xian's table",
        author = "Jia Xian",
        year = 1050,
        summary = "Jia Xian's table of coefficients, used to extract roots of higher powers, is built by adding neighbouring entries row by row, and a short program prints the first seven rows.",
        details = "The table is the arrangement that Western writers later called Pascal's triangle, and it reached later readers through Yang Hui's book of 1261, because Jia Xian's own commentary is lost. The date 1050 is approximate, and the example checks the rows and the fourth power by hand.",
        rare = true,
        instructions = """
            1. Save the example as jia_xian_table.py.
            2. Run it: python3 jia_xian_table.py
        """,
        example = """
            # Each row is built from the row above by adding neighbouring entries.
            rows = [[1]]
            for n in range(1, 7):
                above = rows[-1]
                row = [1] + [above[i] + above[i + 1] for i in range(len(above) - 1)] + [1]
                rows.append(row)
            for n, row in enumerate(rows):
                print(n, " ".join(str(x) for x in row))
            print("row 4 gives the powers in (a + b) to the fourth:", rows[4])
        """,
    ),

)
