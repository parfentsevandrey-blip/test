package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 250 to 259, placed at 251: the atlatl, a spear-thrower whose first use the sources do not date. */
val db179Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.DEVICE,
        section = "Hunting",
        title = "The atlatl, a spear-thrower of the Americas, undated in the sources and placed at 251",
        originalTitle = "Atlatl",
        author = "Hunters and craftsmen of Mesoamerica",
        year = 251,
        summary = "The atlatl is a hand-held lever that throws a spear faster and farther than the arm alone, and the sources do not date its first use, so this record is placed at 251.",
        details = "The sources do not date this device; 251 is the year of the anchor, the death of Decius at Abritus, not of the making. In Mesoamerica the atlatl was the usual launcher, and the records that survive give no date for its first use.",
        rare = true,
    ),

)
