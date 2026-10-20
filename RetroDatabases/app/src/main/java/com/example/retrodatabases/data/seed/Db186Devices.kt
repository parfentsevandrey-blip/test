package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 180 to 190: a placed record for the Roman ship's anchor, undated in the sources and placed at 185 by the execution of Perennis. */
val db186Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.DEVICE,
        section = "Ships",
        title = "The Roman ship's anchor, a weight with flukes that held a vessel to the bottom",
        originalTitle = "Ancora",
        author = "No single author",
        year = 185,
        summary = "Roman ships carried anchors of stone and of iron that held a vessel by weight and flukes, undated in the sources and placed at 185.",
        details = "The sources do not date any particular anchor, so 185 is the year of the anchor event, the execution of Perennis, not the year of the making. This is a placed record, and it rests only on that anchor event.",
        rare = true,
    ),

)
