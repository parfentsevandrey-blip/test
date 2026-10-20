package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 380 to 390: one placed record, the battering ram of the late Roman army, placed at 383 by its anchor. */
val db166Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.DEVICE,
        section = "Siege",
        title = "The battering ram of the late Roman army, a beam swung under a roofed shelter",
        originalTitle = "Aries",
        author = "Roman army engineers",
        year = 383,
        summary = "The battering ram was the common siege engine of the late Roman army, a heavy beam driven against a wall by a team under a roofed shelter.",
        details = "The sources do not date this device; 383 is the year of the anchor, the revolt led by Magnus Maximus that ended with the death of Gratian, and not the year of the making. The ram is a type of machine rather than one dated object, so the record describes the form that late Roman sieges used.",
        rare = true,
    ),

)
