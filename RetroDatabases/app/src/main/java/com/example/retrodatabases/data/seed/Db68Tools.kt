package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1363 to 1367: a Venetian portolan chart, a surgical handbook from Avignon and a planetary treatise known from a copy of 1367. */
val db68Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.TOOL,
        section = "Charts",
        title = "Pizzigani's portolan chart of 1367, a Venetian sea chart",
        originalTitle = "Portolan chart",
        author = "Francesco and Domenico Pizzigani",
        year = 1367,
        summary = "The Pizzigani chart of 1367 is a portolan sea chart from Venice, marked with rhumb lines and place names for coastal sailing.",
        details = "The chart is dated 1367 in the usual accounts and is credited to the brothers Francesco and Domenico Pizzigani. Its rhumb lines and place names served sailors who steered from one port to the next.",
        rare = true,
    ),

    entry(
        databaseId = 68,
        kind = Kind.TOOL,
        section = "Surgery",
        title = "Guy de Chauliac's Chirurgia magna, a surgical handbook of 1363",
        originalTitle = "Chirurgia magna",
        author = "Guy de Chauliac",
        year = 1363,
        summary = "Guy de Chauliac completed his Chirurgia magna in 1363, a surgical handbook that gathered earlier writers and set out operations in order.",
        details = "Chauliac wrote the book at Avignon, where he served the papal court. The date 1363 is the usual date of completion, and later revisions of the text are known.",
    ),

    entry(
        databaseId = 68,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Ibn al-Shatir's planetary treatise, known from a copy of 1367",
        originalTitle = "Nihayat al-sul",
        author = "Ala al-Din Ali ibn Ibrahim al-Ansari, known as Ibn al-Shatir",
        year = 1367,
        summary = "Ibn al-Shatir's planetary treatise Nihayat al-sul is known from a dated copy of 1367, and it revises the models of the sun, moon and planets.",
        details = "No composition date is recorded, so 1367 is the date of the earliest dated copy, and the approximate year is a copy date rather than the date of the work. The treatise replaces the equant with an extra epicycle, and one copy omits the chapter on Mercury.",
        rare = true,
    ),

)
