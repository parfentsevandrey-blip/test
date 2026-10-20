package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of the 450s: the patriarch Anatolius crowns the emperor Leo I in 457. */
val db159Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "The crowning of Leo I by the patriarch Anatolius, an early patriarchal coronation",
        originalTitle = "Stephanosis, the crowning by a patriarch",
        author = "The patriarch Anatolius and the emperor Leo I",
        year = 457,
        summary = "In 457 the patriarch Anatolius of Constantinople crowned the new emperor Leo I, an early instance of a patriarch taking part in an emperor's coronation.",
        details = "Later writers treat the rite as a precedent for a patriarch's part in imperial coronations at Constantinople. Whether it was the first such rite is not certain, because the accounts of earlier accessions say little about who placed the crown.",
    ),

)
