package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 360 to 370: Eutropius's Breviarium, a ten-book history of Rome for the emperor Valens. */
val db168Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.TOOL,
        section = "History",
        title = "Eutropius's Breviarium, a ten-book history of Rome written for Valens",
        originalTitle = "Breviarium ab urbe condita",
        author = "Eutropius",
        year = 369,
        summary = "Eutropius's Breviarium is a short Latin history of Rome in ten books, written in the late 360s and dedicated to the emperor Valens.",
        details = "The history runs from the founding of Rome to 364, and its author was an official who handled imperial petitions. The date is usually given as 369, though some accounts place the work at 369 to 370.",
        rare = true,
    ),

)
