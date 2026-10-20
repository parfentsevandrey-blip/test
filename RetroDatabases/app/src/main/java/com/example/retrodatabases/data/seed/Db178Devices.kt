package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of the 260s: the water mills of Shushtar, undated in the sources and placed at 260 for the anchor of Valerian's capture. */
val db178Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.DEVICE,
        section = "Water",
        title = "The water mills of Shushtar, undated in the sources and placed at 260",
        originalTitle = "Band-e Kaisar",
        author = "Roman captives, by tradition, under Shapur I",
        year = 260,
        summary = "The water mills of Shushtar were driven by canals from the Karun, and tradition ties their dam to Roman captives taken after 260.",
        details = "The sources do not date these mills, so 260 is the year of the anchor, Valerian's capture, and not the year of the making. The dam and bridge called Band-e Kaisar, or Caesar's dam, is attributed to Roman captives by tradition, and UNESCO listed the wider hydraulic system in 2009.",
        rare = true,
    ),

)
