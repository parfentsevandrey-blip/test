package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1293: how a month is placed in one of the two-month terms that Florence's priors served. */
val db75Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.GUIDE,
        section = "Government",
        title = "How a Florentine two-month term of the priors is found from a month",
        originalTitle = "Bimestre",
        author = "The priors of the Florentine commune",
        year = 1293,
        summary = "A month of the year falls in one of six two-month terms, and a short program finds the term of each month by a simple rule.",
        details = "Florence's priors served in two-month terms in the 1290s, so one year held six terms. The example pairs the calendar months for illustration and does not use the exact start day of any term.",
        instructions = """
            1. Save the example as priors_terms.py.
            2. Run it: python3 priors_terms.py
        """,
        example = """
            def term_of(month):
                return (month + 1) // 2

            for month in [1, 2, 3, 4, 7, 8, 12]:
                print("month", month, "falls in term", term_of(month))
            print("terms in a year:", term_of(12))
        """,
    ),

)
