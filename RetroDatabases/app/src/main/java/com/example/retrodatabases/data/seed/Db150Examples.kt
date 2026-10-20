package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 540 to 550: the books of Cosmas Indicopleustes's Christian Topography, counted stage by stage in code. */
val db150Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.EXAMPLE,
        section = "Byzantine",
        title = "The books of Cosmas Indicopleustes's Christian Topography, which grew from five to ten and then to twelve, counted in code",
        originalTitle = "Topographia Christiana",
        author = "Cosmas Indicopleustes",
        year = 547,
        summary = "Cosmas Indicopleustes's Christian Topography grew in stages from five books to twelve, and a short program prints the count at each stage.",
        details = "Dates for the work run from 547 to about 550, and the order of its books is disputed: the Vatican manuscript goes back to an exemplar of ten books, and some editors doubt that books eleven and twelve belong with the main work. The program only adds the stage counts given in the sources, so the total depends on that reading.",
        rare = true,
        instructions = """
            1. Save the example as cosmas_books.py.
            2. Run it: python3 cosmas_books.py
        """,
        example = """
            FIRST_STAGE = 5    # the first books of the Christian Topography
            SECOND_STAGE = 5   # books six to ten, added in one stage
            LATER = 2          # books eleven and twelve, added later
            print("first stage:", FIRST_STAGE)
            print("after the second stage:", FIRST_STAGE + SECOND_STAGE)
            print("books added later:", LATER)
            print("total books:", FIRST_STAGE + SECOND_STAGE + LATER)
        """,
    ),

)
