package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 790 to 800: the Viking longship of the Lindisfarne raid of 793, placed at the year of the raid. */
val db125Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.DEVICE,
        section = "Norse",
        title = "The Viking longship of the Lindisfarne raid of 793, a clinker-built warship of the northern seas",
        originalTitle = "Langskip",
        author = "Norse shipwrights (unnamed)",
        year = 793,
        summary = "The sources do not date the design of the longship, so 793, the year of the sea raid on Lindisfarne, is used as the anchor for this clinker-built warship.",
        details = "The Anglo-Saxon Chronicle records the raid on Lindisfarne in 793, and the raiders came by sea, but no source describes how the ships were built. The year is the anchor, not the date of the making, and the type is known from later finds such as the Oseberg ship, buried about 834.",
        rare = true,
    ),

)
