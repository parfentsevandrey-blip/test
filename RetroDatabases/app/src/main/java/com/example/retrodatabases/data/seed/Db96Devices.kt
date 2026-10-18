package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1080 to 1090: an astrolabe made at Guadalajara in Muslim Spain. */
val db96Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.DEVICE,
        section = "Iberia",
        title = "The Guadalajara astrolabe of 1081/2, credited to Muhammad ibn Sa'id as-Sabban",
        originalTitle = "The Guadalajara astrolabe of 1081/2, credited to Muhammad ibn Sa'id as-Sabban",
        author = "Muhammad ibn Sa'id as-Sabban",
        year = 1081,
        summary = "An astrolabe made at Guadalajara is dated 1081/2 and is credited to Muhammad ibn Sa'id as-Sabban, one of the earliest instruments to survive from Muslim Spain.",
        details = "The date falls across the Christian years 1081 and 1082, and the maker is credited although the instrument is not described as signed. An astrolabe of this kind projects the sky onto a set of plates, so that time and position can be worked out with it.",
        rare = true,
    ),

)
