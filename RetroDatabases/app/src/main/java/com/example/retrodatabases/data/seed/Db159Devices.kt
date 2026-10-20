package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of the 450s, placed: the chorobates, a levelling frame that Roman writers describe, placed at 455, the year of the Vandal sack of Rome. */
val db159Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "The chorobates, a levelling frame with a water channel that Roman writers describe, undated in the sources and placed at 455",
        originalTitle = "Chorobates",
        author = "Vitruvius and later Roman surveyors",
        year = 455,
        summary = "The chorobates was a long wooden frame with a water channel and plumb marks, used by Roman surveyors to find whether the ground was level.",
        details = "The sources do not date the chorobates' use in the fifth century, so 455 is the year of the anchor, the Vandal sack of Rome, and not the year of the making. Roman writers describe the frame as a levelling tool for laying out ground, and the placed year marks the period rather than one object.",
        rare = true,
    ),

)
