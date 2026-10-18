package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1200 to 1210: Fibonacci's arithmetic of 1202 and Stephen Langton's chapter divisions of the Bible. */
val db84Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Fibonacci's Liber abbaci, the book of calculation of 1202",
        originalTitle = "Liber abbaci",
        author = "Leonardo of Pisa, called Fibonacci",
        year = 1202,
        summary = "Fibonacci's Liber abbaci is a Latin arithmetic of 1202 that taught the Hindu-Arabic numerals and their use in trade to readers in Europe.",
        details = "The book gives its date as 1202 in its own preface, and the text that survives is the revised edition of 1228, so accounts differ on what the first version contained. Its topics include the rule of three, money exchange, and problems of partnership and interest.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.TOOL,
        section = "Scripture",
        title = "Stephen Langton's chapter divisions of the Latin Bible",
        originalTitle = "Capitula",
        author = "Stephen Langton",
        year = 1205,
        summary = "Stephen Langton's division of the Latin Bible into chapters, made at Paris about 1205, gave the text the chapter numbers that later readers still use.",
        details = "Accounts differ on the date, with some placing the work at Paris about 1205 and others near 1227, near the end of Langton's life. The attribution rests on medieval manuscripts that carry notes naming him, and the verse numbers came much later, in 1551.",
    ),

)
