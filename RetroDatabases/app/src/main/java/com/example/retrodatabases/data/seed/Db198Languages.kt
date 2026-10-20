package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 60 to 69: Columella's Latin manual of farming, written in the 60s. */
val db198Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.LANGUAGE,
        section = "Farming",
        title = "Columella's De re rustica, a Latin manual of farming written in the 60s",
        originalTitle = "De re rustica",
        author = "Lucius Junius Moderatus Columella",
        year = 60,
        summary = "Columella's De re rustica is a Latin manual of farming, written in the 60s, that covers the layout, staff and crops of a working estate.",
        details = "Scholars date the treatise to the 60s, and 60 is used here as the first year of the usual range of 60 to 65, so the year is approximate. Its first book covers the farm's layout and staff, and the work refers now and then to Varro and Cato, the earlier Roman writers on farming. The text survives in full.",
        rare = true,
    ),

)
