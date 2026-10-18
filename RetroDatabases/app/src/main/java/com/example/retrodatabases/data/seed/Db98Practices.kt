package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1069: the Enkyu review of estate deeds, in which a court office tested the claims of estates. */
val db98Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.PRACTICE,
        section = "Land records",
        title = "The Enkyu review of estate deeds, in which Go-Sanjo's court tested land claims",
        originalTitle = "Enkyu shoen seiri rei",
        author = "Emperor Go-Sanjo and his court",
        year = 1069,
        summary = "In the Enkyu era, which began in 1069, Emperor Go-Sanjo had the court test the deeds behind estate claims, and estates whose deeds could not be shown were liable to return to public control.",
        details = "The review was run through a records office, the Kiroku-sho, which examined the deeds that estate holders produced. The Enkyu era began in the fourth month of 1069, and the reign of Go-Sanjo ended in 1072 or 1073, since accounts differ on the exact year.",
        rare = true,
    ),

)
