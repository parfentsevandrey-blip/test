package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 81: a Roman book-box for rolls, placed at the year Domitian came to power, because the sources do not date the box. */
val db196Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.TOOL,
        section = "Records",
        title = "The Roman book-box, a round case that kept a roll safe",
        originalTitle = "Capsa",
        author = "Roman scribes and booksellers",
        year = 81,
        summary = "The capsa, a round case that kept a book roll safe, is placed at 81, the year Domitian came to power, because the sources do not date the box itself.",
        details = "Roman writers name the capsa as the case that held a roll, but they do not say when such boxes came into use, so 81 is the year of the anchor and not the year of the making. The anchor is Domitian's succession after his brother Titus, and accounts differ by a day or two on the dates of Titus's death and of Domitian's proclamation, so only the year is given.",
        rare = true,
    ),

)
