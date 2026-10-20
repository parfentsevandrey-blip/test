package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 20 to 30: the books of Strabo's Geographica counted by region in code. */
val db202Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.EXAMPLE,
        section = "Books",
        title = "The books of Strabo's Geographica counted by continent",
        originalTitle = "Geographika",
        author = "Strabo of Amaseia",
        year = 23,
        summary = "The seventeen books of Strabo's Geographica fall into an introduction and the regions of Europe, Asia and Africa, and a short program counts the books in each group.",
        details = "Books 1 and 2 are introductory, books 3 to 10 cover Europe, books 11 to 16 cover Asia and book 17 covers Africa, so the program's counts are 2, 8, 6 and 1. The total of 17 checks the division by hand, and the count depends on the division of the books rather than on any date.",
        instructions = """
            1. Save the example as strabo_books.py.
            2. Run it: python3 strabo_books.py
        """,
        example = """
            REGIONS = {
                "introduction": range(1, 3),
                "Europe": range(3, 11),
                "Asia": range(11, 17),
                "Africa": range(17, 18),
            }
            total = 0
            for name, books in REGIONS.items():
                count = len(books)
                total += count
                print(name, count)
            print("books in all:", total)
        """,
    ),

)
