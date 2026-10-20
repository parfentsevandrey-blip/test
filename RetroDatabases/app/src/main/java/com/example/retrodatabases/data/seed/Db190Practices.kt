package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of the 140s: Latin lead curse tablets, placed at 144 with the expulsion of Marcion from the Roman congregation. */
val db190Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.PRACTICE,
        section = "Curses",
        title = "Latin lead curse tablets, spells rolled up and buried against rivals",
        originalTitle = "Defixiones",
        author = "No single author",
        year = 144,
        summary = "Latin curse tablets were thin sheets of lead inscribed with spells against named rivals or thieves, folded or rolled and buried or sunk in water.",
        details = "About five hundred Latin texts survive, mostly on lead, and they fall between the second and early fifth centuries CE. The sources do not date this practice to a year; 144 is the year of the anchor, Marcion's expulsion from the Roman congregation, not of the making.",
        rare = true,
    ),

)
