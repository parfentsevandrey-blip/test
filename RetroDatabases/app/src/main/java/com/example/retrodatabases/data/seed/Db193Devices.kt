package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 110 to 120: the bow drill, a bow-turned shaft that bored stone and metal, placed at 114 for the annexation of Armenia. */
val db193Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.DEVICE,
        section = "Workshops",
        title = "The bow drill, a bow-turned shaft that bored stone and metal",
        originalTitle = "Trypanon",
        author = "Craftsmen of the Greek world",
        year = 114,
        summary = "A bow drill turned a shaft by a cord wound round it and bowed back and forth, and it bored holes in stone, wood and metal.",
        details = "The sources do not date this device or its first use; 114 is the year Trajan annexed Armenia, the anchor for the placed year, not the year of its making. Greek writers call the drill trypanon, and no text gives a date for the first bow drill.",
        rare = true,
    ),

)
