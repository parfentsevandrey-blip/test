package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 570 to 580: Gregory of Tours's short book on the courses of the stars and the times of church services. */
val db147Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.TOOL,
        section = "Frankish",
        title = "Gregory of Tours's De cursu stellarum, a book on the courses of the stars and the times of church services",
        originalTitle = "De cursu stellarum",
        author = "Gregory of Tours",
        year = 573,
        summary = "Gregory of Tours wrote a short book on the courses of the stars and the times of the church services, a reckoning handbook for the Frankish church.",
        details = "Gregory lists this book among his own works in the last book of his History of the Franks, so it was written before he died in 594, but its year is not known. The year 573, when Gregory became bishop of Tours, is a placed year and not the date of the book.",
        rare = true,
    ),

)
