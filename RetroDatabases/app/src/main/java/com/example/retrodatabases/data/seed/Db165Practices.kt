package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 393: the last Olympic games of antiquity, traditionally held at Olympia in 393. */
val db165Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.PRACTICE,
        section = "Games",
        title = "The last Olympic games of antiquity, traditionally held in 393",
        originalTitle = "Olympia",
        author = "The Elean officials at Olympia",
        year = 393,
        summary = "The Olympic games at Olympia are traditionally held for the last time in 393, a date that goes back to a Byzantine chronicle of the eleventh century.",
        details = "The 393 date is traditional and is traced to the chronicle of Kedrenos, and the last games are linked with the reign of Theodosius I. Other accounts give a later end, so the record uses 393 only as the traditional year.",
    ),

)
