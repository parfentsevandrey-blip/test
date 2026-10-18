package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1188: trial by battle, the judicial duel that a legal treatise of about 1188 sets out as a procedure. */
val db86Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.PRACTICE,
        section = "Justice",
        title = "Trial by battle, the judicial duel that Glanvill's treatise describes",
        originalTitle = "Wager of battle",
        author = "English royal courts",
        year = 1188,
        summary = "The wager of battle was a judicial duel that Glanvill's treatise sets out as a procedure for settling some disputed claims in England about 1188.",
        details = "The duel was fought between the parties or their champions, and the verdict of the court followed its outcome. Accounts differ on how often the procedure was used by the late twelfth century, and the date 1188 is the year of the treatise that describes it.",
        rare = true,
    ),

)
