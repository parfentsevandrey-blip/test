package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One placed device of 936: the channels and aqueducts that filled the pools of Madinat al-Zahra, placed at the founding year of the palace city. */
val db111Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.DEVICE,
        section = "Spain",
        title = "The aqueducts and underground channels that filled the pools of Madinat al-Zahra",
        originalTitle = "The aqueducts and underground channels that filled the pools of Madinat al-Zahra",
        author = "The builders of Madinat al-Zahra",
        year = 936,
        summary = "Channels and aqueducts brought water to the pools of the palace city of Madinat al-Zahra.",
        details = "The channels are not dated separately in any text. The year 936, when the palace city was founded, is the placed anchor and not the date of the channels, and excavation of the site, not a dated text, is the source for them. The palace city's pools needed a steady water supply, and the channels served them.",
        rare = true,
    ),

)
