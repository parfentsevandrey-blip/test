package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 360 to 370: Athanasius's yearly festal letters to the churches of Egypt. */
val db168Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "Athanasius's festal letters, the yearly notices of Easter and Lent sent to Egypt",
        originalTitle = "Heortastikai epistolai",
        author = "Athanasius of Alexandria",
        year = 367,
        summary = "Athanasius sent a yearly festal letter to the churches of Egypt that gave the date of Easter and the start of Lent, and the thirty-ninth of these letters is dated 367.",
        details = "Each letter was read to the congregations and set the feast days for the coming year, so the Egyptian churches kept to one reckoning. The letter of 367 is the thirty-ninth in the series, which ran from the early 330s into the last years of his life.",
    ),

)
