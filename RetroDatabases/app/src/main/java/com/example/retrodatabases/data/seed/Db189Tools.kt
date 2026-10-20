package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 150 to 160: Ptolemy's Handy Tables, the planetary tables that count days from the Nabonassar epoch. */
val db189Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.TOOL,
        section = "Planets",
        title = "Ptolemy's Handy Tables, planetary tables for counting positions from a fixed epoch",
        originalTitle = "Ptolemaiou procheiroi kanones",
        author = "Claudius Ptolemy",
        year = 150,
        summary = "Ptolemy's Handy Tables give the places of the Sun, Moon and planets for any date by counting the days from a fixed epoch, as a shorter set of tables beside the Almagest.",
        details = "The composition is dated only to the second century, and 150 is a round date used for it here. The tables count from the Nabonassar era, which began on 26 February 747 BC in the Julian calendar, and they use the Egyptian year of 365 days with no leap days, while the oldest surviving witness dates from the third century.",
        rare = true,
    ),

)
