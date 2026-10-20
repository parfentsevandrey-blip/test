package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 180 to 190: a method that sets out the thirty-six sections of the Yellow Turban rising on a six-column grid. */
val db186Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.GUIDE,
        section = "Grids",
        title = "How thirty-six sections are set out on a six-column grid",
        originalTitle = "Grid of sections",
        author = "No single author",
        year = 184,
        summary = "Thirty-six numbered sections fit a grid of six columns and six rows, and a short program turns a section number into its row and column.",
        details = "The sources give thirty-six sections for the Yellow Turban followers, and the grid is an illustration made for the example, not the real arrangement of the sections. The example prints the row and column of four sections, so each result can be checked by hand.",
        instructions = """
            1. Save the example as sections.py.
            2. Run it: python3 sections.py
        """,
        example = """
            SECTIONS = 36
            COLUMNS = 6
            for section in [1, 6, 7, 36]:
                row = (section - 1) // COLUMNS + 1
                column = (section - 1) % COLUMNS + 1
                print(f"section {section}: row {row}, column {column}")
            print("rows needed:", SECTIONS // COLUMNS)
        """,
        rare = true,
    ),

)
