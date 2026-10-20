package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 100 to 110: the calix, a bronze outlet that limited the flow of a Roman water supply, placed at 109 by the anchor of Trajan's aqueduct. */
val db194Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.DEVICE,
        section = "Water",
        title = "The calix, a bronze outlet that limited how much water a Roman branch pipe took",
        originalTitle = "calix",
        author = "Roman water engineers",
        year = 109,
        summary = "A calix was a bronze outlet set into a channel or tank, and its fixed opening limited the flow of water into a branch of a Roman supply.",
        details = "The sources do not date this device; 109 is the year of the anchor, the Aqua Traiana aqueduct built for Trajan, not the year of the making, and Frontinus's treatise on the aqueducts, dated about 98, already describes such outlets. Some small bronze funnels in Rome's museums have been argued to be liquid measures rather than calices, so the identification is disputed.",
        rare = true,
    ),

)
