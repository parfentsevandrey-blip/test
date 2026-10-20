package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 431: the vallus, a Gallic reaping machine known from Pliny, placed at the year of the Council of Ephesus because no source dates it to the decade. */
val db161Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.DEVICE,
        section = "Farming",
        title = "The vallus, a reaping machine with teeth that Pliny describes in Gaul",
        originalTitle = "Vallus",
        author = "Gallic farmers, as Pliny describes them",
        year = 431,
        summary = "The vallus, a Gallic reaping machine with a row of teeth, is known from Pliny's description and is placed here at the year 431.",
        details = "Pliny writes about the vallus in the first century, and the sources do not date it to the 430s. The year 431 is the year of the placed anchor, the Council of Ephesus, and not the year of the device, so this record is placed and not dated.",
        rare = true,
    ),

)
