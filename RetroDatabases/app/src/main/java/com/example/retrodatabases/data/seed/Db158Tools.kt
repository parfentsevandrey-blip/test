package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 460 to 470: Hydatius's Latin chronicle, a year-by-year reference that runs from 379 into the late 460s. */
val db158Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.TOOL,
        section = "Chronicles",
        title = "Hydatius's Chronicon, a Latin continuation of Jerome from 379 to 468",
        originalTitle = "Chronicon",
        author = "Hydatius of Aquae Flaviae",
        year = 468,
        summary = "Hydatius's Chronicon, a Latin continuation of Jerome's world chronicle from 379, runs into the late 460s and records the last decades of the western empire.",
        details = "The chronicle resumes at 379, where Jerome's translation of Eusebius stops at 378, and some accounts give its coverage as 379 to 468. Some accounts date Hydatius's death to about 469, and the last year of the chronicle is one point where the accounts differ. He was bishop of Aquae Flaviae, usually placed at Chaves, and his entries after 427 read as firsthand reporting, though four parallel dating systems make the late years hard to match to the modern calendar.",
        rare = true,
    ),

)
