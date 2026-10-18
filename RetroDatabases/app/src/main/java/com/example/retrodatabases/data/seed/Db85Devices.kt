package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1190: the sea compass that Alexander Neckam describes in his treatise on the natures of things, the only device of the decade that this batch could verify. */
val db85Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Alexander Neckam's sea compass in De naturis rerum, about 1190",
        originalTitle = "De naturis rerum",
        author = "Alexander Neckam",
        year = 1190,
        summary = "Alexander Neckam's Latin treatise De naturis rerum describes a magnetized needle that sailors used to find direction, one of the earliest European accounts of the compass.",
        details = "Neckam was an English teacher and writer, and his treatise is usually dated to about 1190. Accounts differ on whether this book or his other treatise, De utensilibus, first presented the compass as an aid to navigation, so the claim of priority is disputed.",
        rare = true,
    ),

)
