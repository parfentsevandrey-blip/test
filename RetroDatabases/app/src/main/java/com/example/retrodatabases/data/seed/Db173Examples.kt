package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 310 to 320: the fifteen books of Eusebius's Preparation for the Gospel, counted in code. */
val db173Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.EXAMPLE,
        section = "Books",
        title = "The fifteen books of Eusebius's Preparation for the Gospel, counted in code",
        originalTitle = "Praeparatio evangelica",
        author = "Eusebius of Caesarea",
        year = 313,
        summary = "Eusebius's Preparation for the Gospel is a Greek work in fifteen books, and a short program numbers the books and totals them.",
        details = "Eusebius began the work about 313 and dedicated it to Theodotus, bishop of Laodicea, as the companion to his Demonstration of the Gospel. The example prints the number of books and their total, so the count of fifteen can be checked by hand.",
        instructions = """
            1. Save the example as pe_books.py.
            2. Run it: python3 pe_books.py
        """,
        example = """
            books = list(range(1, 16))
            print("books:", len(books))
            print("sum of the book numbers:", sum(books))
            print("last book:", books[-1])
        """,
        rare = true,
    ),

)
