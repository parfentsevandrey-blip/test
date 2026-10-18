package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 980 to 990: Ibn Sahl's treatise on burning mirrors and lenses, a study of curved surfaces that focus light. */
val db106Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.DEVICE,
        section = "Baghdad",
        title = "Ibn Sahl's treatise on burning mirrors and lenses, a study of curved surfaces that focus light",
        originalTitle = "Kitab al-Harraqat",
        author = "Ibn Sahl",
        year = 984,
        summary = "Ibn Sahl's treatise on burning mirrors and lenses, written around 984, is an early study of curved mirrors and lenses that focus light.",
        details = "Roshdi Rashed places the composition between 983 and 985, and the surviving copies give the Arabic title in different forms. The lens profiles it works out are free of aberration, the type later called anaclastic.",
        rare = true,
    ),

)
