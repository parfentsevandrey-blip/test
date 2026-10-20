package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 341: stichometry, the counting of a Greek book's lines to fix the price of a copy, placed at 341 for the council of Antioch. */
val db170Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.PRACTICE,
        section = "Copying",
        title = "Stichometry, the counting of a Greek book's lines to fix the price of a copy, placed at 341 for the council of Antioch",
        originalTitle = "Stichometry",
        author = "Greek scribes and booksellers",
        year = 341,
        summary = "Stichometry was the counting of the lines, called stichoi, of a book, so that a scribe or bookseller could fix the price of a copy by its length.",
        details = "The sources do not date this practice; 341 is the year of the anchor, the council of Antioch, and not the year the counting began. Lists that give the line counts of Greek books survive from later centuries, so the practice is known from those records rather than from its start.",
        rare = true,
    ),

)
