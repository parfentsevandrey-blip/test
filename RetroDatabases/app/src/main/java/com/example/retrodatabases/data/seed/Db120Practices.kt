package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 840 to 850: the Feast of Orthodoxy, kept each year from the restoration of the holy images in 843. */
val db120Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.PRACTICE,
        section = "Byzantine",
        title = "The Feast of Orthodoxy, a yearly feast kept from the restoration of images in 843",
        originalTitle = "Kyriake tes Orthodoxias",
        author = "Patriarch Methodius and the regent Theodora",
        year = 843,
        summary = "The Feast of Orthodoxy, kept each year in the Byzantine church, commemorates the restoration of the holy images in 843 after the iconoclast period.",
        details = "In March 843 the regent Theodora and Patriarch Methodius restored the veneration of images, and the day was afterwards kept each year on the first Sunday of Lent. The feast still marks that restoration in many Orthodox churches.",
        rare = true,
    ),

)
