package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 130 to 140: Theon of Smyrna's Greek handbook of the mathematics that helps a reader of Plato. */
val db191Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Theon of Smyrna's Mathematics useful for reading Plato",
        originalTitle = "Expositio rerum mathematicarum ad legendum Platonem utilium",
        author = "Theon of Smyrna",
        year = 130,
        summary = "Theon of Smyrna wrote a Greek handbook of the mathematical subjects that help a reader understand Plato, covering number, music, astronomy and geometry.",
        details = "The book itself is undated, so the year 130 is an estimate for the years of the author's career, which Ptolemy's observations of 127 to 132 help to fix, and not a date for the writing. Accounts differ on Theon's dates: one reference gives a floruit of 100, while another puts his death near 135.",
        rare = true,
    ),

)
