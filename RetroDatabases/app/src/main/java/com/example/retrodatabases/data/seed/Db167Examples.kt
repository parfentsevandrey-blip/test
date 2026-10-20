package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 379: the thirteen books and 465 propositions of Euclid's Elements counted in code, placed at the year Theodosius I's reign begins. */
val db167Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.EXAMPLE,
        section = "Books",
        title = "Euclid's thirteen books, with 465 propositions counted by book in code",
        originalTitle = "Stoicheia",
        author = "Euclid",
        year = 379,
        summary = "Euclid's Elements has thirteen books and 465 propositions, and a short program adds up the count of each book and of each group of books.",
        details = "The sources do not date this count; 379 is the year the reign of Theodosius I begins, the anchor placed here and not the date of the count. Euclid wrote the Elements about 300 BCE, and the counts follow the usual numbering of the text.",
        instructions = """
            1. Save the example as elements.py.
            2. Run it: python3 elements.py
        """,
        example = """
            books = [48, 14, 37, 16, 25, 33, 39, 27, 36, 115, 39, 18, 18]   # propositions in books I to XIII
            print("books:", len(books))
            print("propositions in all:", sum(books))
            print("books I to IV:", sum(books[0:4]))
            print("books V to IX:", sum(books[4:9]))
            print("books X to XIII:", sum(books[9:13]))
        """,
    ),

)
