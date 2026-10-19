package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 700 to 710: the cremation of the monk Dosho in 700 and the Wado kaichin copper coins struck from 708. */
val db134Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "The cremation of the monk Dosho in 700, the first cremation recorded in Japan",
        originalTitle = "kaso",
        author = "Japanese court chroniclers",
        year = 700,
        summary = "The monk Dosho died in 700 and his body was cremated, the first cremation recorded in Japan.",
        details = "Dosho had studied in Tang China and brought Buddhist teaching back to Japan. The record of his cremation comes from a later national chronicle, not from a note made at the time.",
        rare = true,
    ),

    entry(
        databaseId = 134,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "The Wado kaichin copper coins, struck from 708, with the era name Wado taken from copper found at Chichibu",
        originalTitle = "Wado kaichin",
        author = "The Japanese court",
        year = 708,
        summary = "The Wado kaichin copper coins were struck by the Japanese court from 708, and their reading matches the era name Wado, meaning Japanese copper.",
        details = "Copper found at Chichibu in Musashi in 708 led the court to take the era name Wado, and the coins share its reading. Accounts differ on how widely the coins circulated in their early years, and the court's aims in striking them are discussed.",
        rare = true,
    ),

)
