package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 50 to 59: the Meroitic script of Kush, placed at 54 by its anchor because the sources do not date its invention. */
val db199Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.SYSTEM,
        section = "Writing",
        title = "The Meroitic script of Kush, two forms of writing whose language is only partly read",
        originalTitle = "Meroitic script",
        author = "Scribes of the Kushite kingdom at Meroe",
        year = 54,
        summary = "The Meroitic script of the Kushite kingdom has a hieroglyphic and a cursive form, and its signs can be read while the language they write is only partly understood.",
        details = "The sources do not date the invention of this script, so 54 is the year of the anchor, the accession of Nero, and not the year of its making. Francis Llewellyn Griffith worked out most of the sound values of the signs in the early 1900s, and the inscriptions go on for several centuries, down to about the fourth century CE.",
        rare = true,
    ),

)
