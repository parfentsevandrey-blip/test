package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 300 to 310: a Roman treadwheel crane that the sources do not date, placed at 301 for the year of the Edict on Maximum Prices. */
val db174Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.DEVICE,
        section = "Lifting",
        title = "The Roman treadwheel crane, a wheel turned by men walking inside it to lift loads",
        originalTitle = "Tympanum",
        author = "Roman builders, as Vitruvius describes them",
        year = 301,
        summary = "The Roman treadwheel crane, a wheel turned by men walking inside it, lifted heavy loads on building sites.",
        details = "The sources do not date this device; 301 is the year of the anchor, the Edict on Maximum Prices, and not the year of the making. Vitruvius describes the wheel and the men who walked in it, but nothing in his account dates the first use of the machine.",
        rare = true,
    ),

)
