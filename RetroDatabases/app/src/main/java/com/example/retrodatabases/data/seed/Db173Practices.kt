package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 310 to 320: freeing a slave in church and taking a lawsuit to a bishop, both under Constantine's laws. */
val db173Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.PRACTICE,
        section = "Law",
        title = "Freeing a slave in church before the congregation, allowed by Constantine's law of 316",
        originalTitle = "Manumissio in ecclesia",
        author = "Constantine",
        year = 316,
        summary = "Constantine's law of 316 allowed a master to free a slave in church before the congregation.",
        details = "The law is kept in the Theodosian Code, and it gives a freeing made before the people the force of a legal manumission.",
        rare = true,
    ),

    entry(
        databaseId = 173,
        kind = Kind.PRACTICE,
        section = "Law",
        title = "Taking a lawsuit to a bishop's court, allowed by Constantine's law of 318",
        originalTitle = "Episcopalis audientia",
        author = "Constantine",
        year = 318,
        summary = "Constantine's law of 318 allowed the parties in a lawsuit to take their case to a bishop, whose judgment the law made binding.",
        details = "The law is kept in the Theodosian Code, and it gave a bishop's judgment a binding force in civil cases.",
        rare = true,
    ),

)
