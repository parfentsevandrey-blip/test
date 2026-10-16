package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1410 to 1420: the Orloj, Prague's astronomical clock, completed in 1410 according to most accounts. */
val db63Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Prague's astronomical clock, the Orloj",
        originalTitle = "Orloj",
        author = "Mikulas of Kadan and Jan Sindel",
        year = 1410,
        summary = "Prague's astronomical clock, the Orloj, stands on the Old Town Hall tower and shows the hours with the places of the sun and the moon.",
        details = "Most accounts name Mikulas of Kadan and Jan Sindel as its makers and give 1410 for its completion, but other accounts give different dates, so this record uses the date most often given. The clock is set in the tower of the Old Town Hall in Prague.",
    ),

)
