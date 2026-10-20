package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 150 to 160: Ptolemy's Almagest in its original Greek and his Guide to Geography, both dated about 150. */
val db189Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.LANGUAGE,
        section = "Astronomy",
        title = "Ptolemy's Almagest in its original Greek, a book of astronomy",
        originalTitle = "Mathematike syntaxis",
        author = "Claudius Ptolemy",
        year = 150,
        summary = "Ptolemy's Almagest, written in Greek at Alexandria about 150, is the chief surviving work of ancient mathematical astronomy, with a star catalogue and a theory of the planets.",
        details = "The book is in thirteen books, and its star catalogue lists 1022 stars in 48 constellations in the usual count, although modern lists give other totals. It was known in Arabic as al-Majisti, from the Greek megiste, and this record covers only the Greek original, not the later translations and epitomes.",
    ),

    entry(
        databaseId = 189,
        kind = Kind.LANGUAGE,
        section = "Geography",
        title = "Ptolemy's Guide to Geography, a Greek list of places by latitude and longitude",
        originalTitle = "Geographike hyphegesis",
        author = "Claudius Ptolemy",
        year = 150,
        summary = "Ptolemy's Guide to Geography, written in Greek about 150, gives the latitude and longitude of the places of the known world and explains how to draw maps of them.",
        details = "The work is in eight books, and its lists of places are one of the main ancient sources of coordinates for the inhabited world. It is usually dated to about 150, and the printed maps later made from it are not part of this record.",
    ),

)
