package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 180 to 190: a placed record for stone rubbing, placed at 183 by the completion of the Xiping stones. */
val db186Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.PRACTICE,
        section = "Copying",
        title = "Stone rubbing, paper pressed on an inscribed stone and inked",
        originalTitle = "Tuoben",
        author = "No single author",
        year = 183,
        summary = "A rubbing copies an inscription by pressing damp paper onto the carved stone and inking the raised letters, undated in the sources and placed at 183.",
        details = "The sources do not date the first rubbing, so 183 is the year of the anchor, the completion of the Xiping stones, not the year of any rubbing. The stones were meant to fix one standard text against tampering, which suits them as an anchor for a copying practice. This is a placed record.",
        rare = true,
    ),

)
