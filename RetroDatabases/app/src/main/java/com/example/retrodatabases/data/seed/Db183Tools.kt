package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 210 to 220, placed at 213: the Roman strigil, a curved scraper for oil and dirt after bathing. */
val db183Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.TOOL,
        section = "Bathing",
        title = "The Roman strigil, a curved metal scraper for oil and dirt after bathing",
        originalTitle = "Strigilis",
        author = "Unknown Roman makers",
        year = 213,
        summary = "A strigil is a curved metal scraper that Greek and Roman athletes and bathers used to remove oil, sweat and dirt from the skin.",
        details = "The sources do not date this tool; 213 is the year of the anchor, Caracalla's campaign against the Alamanni, when he took the title Germanicus, not the year of the making. Strigils survive in bronze and iron from bath sites and burials, and their shapes are dated only in broad periods, so the placed year says nothing about any single example.",
        rare = true,
    ),

)
