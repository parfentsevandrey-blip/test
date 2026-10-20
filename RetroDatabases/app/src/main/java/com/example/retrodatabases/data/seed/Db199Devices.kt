package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 50 to 59: the Roman military horn, placed at 59 by its anchor because the sources do not date its first use. */
val db199Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.DEVICE,
        section = "Military",
        title = "The Roman military horn, a bronze instrument whose calls passed orders across a camp",
        originalTitle = "Cornu",
        author = "Roman army trumpeters and their officers",
        year = 59,
        summary = "The Roman military horn was a bronze instrument whose calls passed orders across a camp or a battle line.",
        details = "The sources do not date the first use of the military horn, so 59 is the year of the anchor, the murder of Agrippina, and not the year of the instrument's making. Roman writers describe the calls of horns and trumpets in battle, and each call stood for a set order, so the calls worked as a short code.",
        rare = true,
    ),

)
