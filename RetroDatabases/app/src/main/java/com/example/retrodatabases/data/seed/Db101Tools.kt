package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1030: al-Biruni's astronomical handbook with tables, written for a Ghaznavid sultan. */
val db101Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "al-Biruni's Mas'udic Canon, an astronomical handbook with tables, dedicated to Mas'ud of Ghazni",
        originalTitle = "Al-Qanun al-Mas'udi",
        author = "al-Biruni",
        year = 1030,
        summary = "The Mas'udic Canon is an Arabic handbook of astronomy with tables, which al-Biruni completed in 1030 and dedicated to the Ghaznavid sultan Mas'ud.",
        details = "It gathers tables for the sun, moon and planets, with trigonometric values and rules for using them. The year 1030 is the usual date for its completion, and the sultan's name gives the book its title.",
        rare = true,
    ),

)
