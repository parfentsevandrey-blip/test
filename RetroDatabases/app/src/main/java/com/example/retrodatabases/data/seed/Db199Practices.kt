package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 50 to 59: Roman pupils copying the alphabet from a written model, placed at 50 by its anchor. */
val db199Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.PRACTICE,
        section = "Schools",
        title = "Roman pupils copying the alphabet line by line from a written model",
        originalTitle = "Abecedarium",
        author = "Roman schoolmasters and their pupils",
        year = 50,
        summary = "Roman pupils learned their letters by copying the alphabet line by line from a model written out by the master, and the school tablets that carry such models survive.",
        details = "The sources do not date when this practice began, so 50 is the year of the anchor, the founding of the colony at Cologne, and not the year of the first model. The surviving school tablets with the alphabet in order come from several parts of the Roman world.",
        rare = true,
    ),

)
