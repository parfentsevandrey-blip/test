package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 580 to 590: how the wards of a grid capital such as Daxing are addressed by row and column, worked out by a program. */
val db146Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.GUIDE,
        section = "Chinese",
        title = "How the wards of a grid capital such as Daxing are addressed by row and column, worked out by a program",
        originalTitle = "Daxing cheng",
        author = "Sui builders of Daxing",
        year = 583,
        summary = "The Sui capital Daxing was laid out on a grid of wards cut by straight avenues, and a short program numbers the wards of a grid row by row.",
        details = "The court moved to Daxing in 583, and its plan divides the city into wards by a regular grid. The program applies row and column reckoning to a sample grid of three rows and four columns, which is not the real count of Daxing's wards.",
        rare = true,
        instructions = """
            1. Save the example as ward_grid.py.
            2. Run it: python3 ward_grid.py
        """,
        example = """
            ROWS = 3
            COLUMNS = 4

            def ward_number(row, column):
                return row * COLUMNS + column + 1

            for row in range(ROWS):
                print(" ".join(str(ward_number(row, column)) for column in range(COLUMNS)))
            print("ward in row 2, column 3:", ward_number(2, 3))
        """,
    ),

)
