package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 440 to 450: the seven books of Socrates Scholasticus's church history and the years they cover, counted in code. */
val db160Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.EXAMPLE,
        section = "Histories",
        title = "The seven books of Socrates Scholasticus's church history and the years from 305 to 439 they cover, counted in code",
        originalTitle = "Historia ecclesiastica",
        author = "Socrates Scholasticus",
        year = 440,
        summary = "Socrates Scholasticus wrote his Ecclesiastical History in seven Greek books, and a short program counts the years they cover and the books.",
        details = "The usual span runs from 305 to 439, which gives 135 years when both ends are counted, although some accounts carry the history on to 445. The work is usually dated to about 440, and the program prints the years, the books and the years per book rounded down.",
        instructions = """
            1. Save the example as socrates_years.py.
            2. Run it: python3 socrates_years.py
        """,
        example = """
            first_year = 305
            last_year = 439
            books = 7
            years = last_year - first_year + 1
            print("years covered:", years)
            print("books:", books)
            print("years per book, rounded down:", years // books)
        """,
    ),

)
