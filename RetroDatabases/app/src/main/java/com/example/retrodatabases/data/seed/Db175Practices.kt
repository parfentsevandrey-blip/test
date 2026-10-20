package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 290 to 300: the adoratio before the emperor's purple robes, placed at 293 because its introduction is not dated. */
val db175Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.PRACTICE,
        section = "Court",
        title = "The adoratio, prostration before the emperor's purple robes at court",
        originalTitle = "Adoratio purpurae",
        author = "Diocletian, as later accounts tell it",
        year = 293,
        summary = "The adoratio was the act of prostrating oneself before the emperor's purple robes at court, a custom that later accounts credit to Diocletian.",
        details = "Accounts differ on its origin: an older view puts the custom before Diocletian, since Alexander Severus is said to have forbidden it. The sources do not date its introduction; 293, the year of the tetrarchy, is the anchor for this placed record, not the year the custom began.",
        rare = true,
    ),

)
