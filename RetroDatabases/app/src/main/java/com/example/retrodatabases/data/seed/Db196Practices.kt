package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 84: the parchment codex as a Saturnalia gift in Martial's couplets, an early literary sign of the bound book. */
val db196Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "The parchment codex as a Saturnalia gift in Martial's couplets",
        originalTitle = "Apophoreta",
        author = "Martial",
        year = 84,
        summary = "Martial's Saturnalia couplets of about 84 offer parchment notebooks among the gifts, an early literary sign of the bound parchment codex.",
        details = "Accounts date the Apophoreta to 84 or 85, and 84 is used here. The usual reading takes the parchment notebooks, the pugillares membranei, as codices, but some scholars think only some of the lots were codices, so the claim is left open.",
        rare = true,
    ),

)
