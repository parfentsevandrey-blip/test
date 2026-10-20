package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 274: Aurelian's festival of the Unconquered Sun on 25 December, in the year of his temple to the Sun at Rome. */
val db177Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.SYSTEM,
        section = "Religion",
        title = "Aurelian's festival of the Unconquered Sun on 25 December",
        originalTitle = "Dies natalis Solis Invicti",
        author = "Emperor Aurelian",
        year = 274,
        summary = "Aurelian promoted the cult of the Unconquered Sun and built a temple to the Sun at Rome in 274, and the Roman calendar of 354 lists a festival of the Sun on 25 December.",
        details = "The 25 December date appears in the calendar of 354, but accounts differ on whether Aurelian himself fixed that day. The record uses 274, the year of his temple to the Sun, as the year the festival began, and that choice is a date of convenience rather than a settled one.",
    ),

)
