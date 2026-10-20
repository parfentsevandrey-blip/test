package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 160 to 170: a Han double-acting piston bellows, undated in the sources and placed at 168, the first year of Emperor Ling. */
val db188Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.DEVICE,
        section = "Bellows",
        title = "The double-acting piston bellows of the Han ironworks, placed at 168",
        originalTitle = "Piston bellows",
        author = "No single author",
        year = 168,
        summary = "Han ironworkers are thought to have blown air with piston bellows that pushed on both strokes, and the device is placed at 168 because no source dates it.",
        details = "The sources do not date this device; 168 is the year of the anchor, the first year of Emperor Ling of Han, not of the making. The evidence for Han use is mainly archaeological, and the earliest full written descriptions are much later, so the placement is weak.",
        rare = true,
    ),

)
