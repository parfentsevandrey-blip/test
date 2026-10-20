package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 680 to 690: the Book of Durrow, an Irish copy of the four Gospels made about 680. */
val db136Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.PRACTICE,
        section = "Irish",
        title = "The Book of Durrow, an Irish gospel book with carpet pages, made about 680",
        originalTitle = "Book of Durrow",
        author = "Irish scribes and illuminators",
        year = 680,
        summary = "The Book of Durrow is an Irish copy of the four Gospels with carpet pages and interlace, dated to about 680.",
        details = "Most accounts date it to about 680, though some place it from about 650 to 700. It is kept at Trinity College Dublin, and it is one of the earliest surviving Insular gospel books.",
    ),

)
