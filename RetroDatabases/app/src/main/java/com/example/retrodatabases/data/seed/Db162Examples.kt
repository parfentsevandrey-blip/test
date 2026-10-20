package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 426: the twenty-two books of Augustine's City of God, split at book ten and counted in code. */
val db162Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.EXAMPLE,
        section = "Books",
        title = "The twenty-two books of the City of God split at book ten, counted in code",
        originalTitle = "De civitate Dei",
        author = "Augustine of Hippo",
        year = 426,
        summary = "Augustine's City of God has twenty-two books, and a short program splits them at book ten, where the books against the pagans end and the books on the two cities begin.",
        details = "Books one to ten answer the pagan critics, and books eleven to twenty-two treat the origin, course and end of the two cities. The program only counts the books in each part, so the output can be checked against the division by hand.",
        instructions = """
            1. Save the example as city_books.py.
            2. Run it: python3 city_books.py
        """,
        example = """
            books = list(range(1, 23))
            against_pagans = [book for book in books if book <= 10]
            two_cities = [book for book in books if book > 10]
            print("books against the pagans:", len(against_pagans))
            print("books on the two cities:", len(two_cities))
            print("books in all:", len(books))
            print("last book on the two cities:", two_cities[-1])
        """,
    ),

)
