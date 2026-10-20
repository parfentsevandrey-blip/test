package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 290 to 300: the hypocaust, a Roman underfloor heating system, placed at 293 because its date is not given. */
val db175Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.DEVICE,
        section = "Heating",
        title = "The hypocaust, a Roman underfloor heating system for baths and villas",
        originalTitle = "Hypocaustum",
        author = "Roman builders",
        year = 293,
        summary = "The hypocaust was a Roman underfloor heating system that warmed the rooms of baths and villas with hot air from a furnace, and the sources do not date it, so it is placed here at 293.",
        details = "The sources do not date this device; 293 is the year of the anchor, the naming of the Caesars, not the year of its making. Hypocausts were built in Roman baths and villas over several centuries, so the system belongs to the Roman period as a whole.",
        rare = true,
    ),

)
