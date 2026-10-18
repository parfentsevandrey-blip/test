package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1210 to 1219: the chain that the Ayyubid garrison stretched across the Nile at Damietta, dated approximately. */
val db83Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.DEVICE,
        section = "Defence",
        title = "The chain stretched across the Nile at Damietta, 1218",
        originalTitle = "Silsila",
        author = "The Ayyubid garrison of Damietta",
        year = 1218,
        summary = "The Ayyubid defenders of Damietta stretched a chain across the Nile to block the crusader fleet, and in August 1218 the crusaders took the tower that guarded it.",
        details = "The chain was part of the siege of Damietta, which began in 1218 and ran into 1219. The year is approximate because the day the chain was first stretched is not recorded precisely, so 1218 is the year of its first public form in the siege accounts.",
        rare = true,
    ),

)
