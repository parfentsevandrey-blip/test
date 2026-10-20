package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 376: a rotary hand-mill, placed at the year of the Gothic crossing because the sources do not date it. */
val db167Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.DEVICE,
        section = "Roman",
        title = "The rotary hand-mill, or quern, of the Roman provinces",
        originalTitle = "Mola manuaria",
        author = "Makers of Roman hand-mills",
        year = 376,
        summary = "The rotary hand-mill, a pair of stones turned by hand to grind grain, was in use across the Roman provinces, and the sources do not date it.",
        details = "The sources do not date this device; 376 is the year of the anchor, the Gothic crossing of the Danube, not of the making. Rotary hand-mills of this kind are known from the stones that survive in the provinces rather than from any written account.",
        rare = true,
    ),

)
