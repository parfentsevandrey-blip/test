package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 410 to 418: the brass hydroscope that Synesius asked Hypatia to make, a placed record with no dated device. */
val db163Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.DEVICE,
        section = "Measure",
        title = "The brass hydroscope that Synesius asked Hypatia to make",
        originalTitle = "Hydroskopion",
        author = "Synesius of Cyrene and Hypatia of Alexandria",
        year = 415,
        summary = "Synesius asked the Alexandrian teacher Hypatia for a brass hydroscope, an instrument for judging the density of water, and the sources do not date the device itself.",
        details = "The request is in Synesius's Letter 15, which is usually dated to 402, and the letter asks for the hydroscope to be cast in brass. The sources do not date this device, so 415, the year Hypatia was killed in Alexandria, is the anchor year of this record and not the year of its making.",
        rare = true,
    ),

)
