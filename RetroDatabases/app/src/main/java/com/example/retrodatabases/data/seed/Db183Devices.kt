package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 210 to 220: the Barbegal mill near Arles, placed at 211 because its own date is an estimate in the second century. */
val db183Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.DEVICE,
        section = "Milling",
        title = "The Barbegal mill, sixteen water wheels in two banks near Arles",
        originalTitle = "Moulin de Barbegal",
        author = "Unknown builders near Arles",
        year = 211,
        summary = "The Barbegal mill at Fontvieille near Arles had sixteen water wheels in two banks of eight, set down a steep slope and fed by an aqueduct.",
        details = "Most accounts date the mill to the second century AD, and some start it at the end of the first century, so the mill itself is not dated to this decade. The year 211 is the year of the anchor, the death of Septimius Severus at York, not the year of the making.",
        rare = true,
    ),

)
