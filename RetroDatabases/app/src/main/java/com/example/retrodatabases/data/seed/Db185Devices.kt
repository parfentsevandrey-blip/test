package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 190 to 200: the Chinese wheelbarrow, undated in the sources and placed at 197 by the Parthian campaign. */
val db185Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.DEVICE,
        section = "Transport",
        title = "The Chinese wheelbarrow, a one-wheeled barrow pushed by hand",
        originalTitle = "Du lun che",
        author = "No known maker",
        year = 197,
        summary = "The one-wheeled barrow of China is undated in the sources, so this record places it at 197, the year of the Parthian campaign that serves as its anchor.",
        details = "No source gives a date for the first wheelbarrow, and the earliest securely dated pictures of one-wheeled barrows are painted and carved scenes in Han tombs of the second century AD. The year 197 is the year of the anchor, Severus's Parthian campaign, and not the date of the barrow, since the pictures show barrows in use before that year.",
        rare = true,
    ),

)
