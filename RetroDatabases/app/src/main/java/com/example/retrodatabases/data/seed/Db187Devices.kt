package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 175: the talking drum of West Africa, a placed record whose year is the revolt of Avidius Cassius. */
val db187Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.DEVICE,
        section = "Signals",
        title = "The talking drum of West Africa, a tuned drum that copies the tones of speech",
        originalTitle = "dundun",
        author = "Drummers of the Yoruba and neighbouring peoples",
        year = 175,
        summary = "The talking drum is an hourglass-shaped drum whose pitch follows the tones of speech, so a drummer can send a spoken message across a distance.",
        details = "This is a placed record: the sources do not date the talking drum, so 175 is the year of the anchor, the revolt of Avidius Cassius in Syria in spring 175, and not the year of the drum's making. The record describes the drum in general terms and names no single maker.",
        rare = true,
    ),

)
