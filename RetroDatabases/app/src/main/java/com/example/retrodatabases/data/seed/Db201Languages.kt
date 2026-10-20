package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 30 to 40: Velleius Paterculus's two-book Roman history and Valerius Maximus's nine books of examples. */
val db201Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Velleius Paterculus's Roman history, two books from Troy to 30",
        originalTitle = "Historiae Romanae",
        author = "Velleius Paterculus",
        year = 30,
        summary = "Velleius Paterculus wrote a two-book Latin history of Rome from the fall of Troy down to about 30, addressed to Marcus Vinicius in his consulship.",
        details = "The work is the only surviving writing of its author, and its second book gives much of its space to Augustus and Tiberius. Accounts differ on whether the narrative ends in 29 or 30, but the year 30 is the usual date, named by the consulship of Vinicius.",
        rare = true,
    ),

    entry(
        databaseId = 201,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Valerius Maximus's book of memorable deeds and sayings, nine books of examples",
        originalTitle = "Factorum et dictorum memorabilium libri IX",
        author = "Valerius Maximus",
        year = 31,
        summary = "Valerius Maximus gathered Roman and foreign examples of memorable deeds and sayings into nine books for speakers and students, and dedicated the collection to the emperor Tiberius.",
        details = "The examples are sorted under topic headings, so that a speaker could find a case to quote for most subjects. The usual date is about 31, set by the dedication to Tiberius, and the year is an estimate from that dedication.",
        rare = true,
    ),

)
