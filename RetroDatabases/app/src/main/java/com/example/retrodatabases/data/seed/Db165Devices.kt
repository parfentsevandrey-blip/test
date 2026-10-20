package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 390: the onager, a torsion catapult that Ammianus describes, placed at 390 because the sources do not date it. */
val db165Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.DEVICE,
        section = "Siege engines",
        title = "The onager, a torsion catapult that Ammianus describes, placed at 390",
        originalTitle = "Onager",
        author = "Unknown Roman makers",
        year = 390,
        summary = "The onager was a torsion catapult that threw stones from a twisted cord, and Ammianus describes one in a digression on siege engines.",
        details = "The sources do not date this device; 390 is the year of the anchor, not of the making, and the anchor is Ammianus's history, usually dated to about 390. The name is said to come from the wild ass, whose kick the machine's recoil resembles, and the description sits in a digression within the surviving books of the history.",
        rare = true,
    ),

)
