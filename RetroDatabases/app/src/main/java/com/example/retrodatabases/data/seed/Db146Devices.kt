package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 580 to 590: the shadow gnomon of the Sui court, undated in its sources and placed at 583. */
val db146Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.DEVICE,
        section = "Chinese",
        title = "The shadow gnomon of the Sui court, an upright pole that read the noon shadow of the sun, undated in its sources and placed at 583",
        originalTitle = "gui biao",
        author = "Sui court astronomers",
        year = 583,
        summary = "A pole and a graduated board, the gnomon, measured the length of the sun's noon shadow to follow the solar year, and the sources do not date the Sui instrument.",
        details = "The sources do not date this device; 583 is the year of the anchor, the court's move to the new capital of Daxing, not the year of the gnomon's making. The gui biao of Chinese astronomy paired a vertical pole with a flat board marked in units, and astronomers read the noon shadow at the solstices, a method older than the Sui.",
        rare = true,
    ),

)
