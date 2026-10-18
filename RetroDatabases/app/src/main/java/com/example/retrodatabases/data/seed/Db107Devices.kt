package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 977 to 979: Zhang Sixun's clock-tower armillary sphere of the Song, with its drive described as mercury. */
val db107Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.DEVICE,
        section = "Song China",
        title = "Zhang Sixun's clock-tower armillary sphere, completed in 977 or 979",
        originalTitle = "Zhang Sixun's armillary sphere",
        author = "Zhang Sixun",
        year = 977,
        summary = "Zhang Sixun, a tenth-century astronomer and engineer, built a clock-tower armillary sphere that was completed in 977 or 979.",
        details = "Accounts give 977 or 979 for its completion, and its drive was mercury rather than water, with a water clock regulating the drip. The first water-powered armillary sphere is credited to the Han engineer Zhang Heng, which makes this Song instrument a later step in that line.",
        rare = true,
    ),

)
