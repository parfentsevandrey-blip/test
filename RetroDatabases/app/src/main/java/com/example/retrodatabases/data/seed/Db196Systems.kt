package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 80: the opening of the Flavian amphitheatre under Titus, with the numbered arches and seat tickets that sorted its crowds. */
val db196Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.SYSTEM,
        section = "Amphitheatre",
        title = "The numbered arches and seat tickets of the Flavian amphitheatre",
        originalTitle = "Amphitheatrum Flavium",
        author = "Vespasian and Titus",
        year = 80,
        summary = "The Flavian amphitheatre, opened under Titus in 80, numbered its ground-level arches and sorted its crowds of tens of thousands into seats by entrance and section.",
        details = "Most accounts date the opening to 80, though some give 81, and the building had about eighty ground-level entrances. Tickets called tesserae gave the entrance and the seating section, so a crowd of that size could be counted in and seated by a set plan.",
        rare = true,
    ),

)
