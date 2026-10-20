package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 480 to 490: the striking of a bishop's name from the diptychs, the altar lists, in 484. */
val db156Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.PRACTICE,
        section = "Liturgy",
        title = "Striking a bishop's name from the diptychs, the altar lists, as Acacius did to Felix III in 484",
        originalTitle = "Diptycha",
        author = "Acacius of Constantinople and Pope Felix III",
        year = 484,
        summary = "The diptychs were lists of living and dead names read at the altar, and striking a name from them marked a break in communion, as Acacius of Constantinople did to Pope Felix III in 484.",
        details = "The lists were read during the liturgy, so a name taken out of them was a visible sign that communion had been broken. The act of 484 began the Acacian schism, which lasted until 519.",
        rare = true,
    ),

)
