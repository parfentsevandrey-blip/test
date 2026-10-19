package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 840 to 850: the beacon towers of Leo the Mathematician, a placed record at 842, the year Theophilos died. */
val db120Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.DEVICE,
        section = "Byzantine",
        title = "The beacon towers of Leo the Mathematician, built in Theophilos's reign",
        originalTitle = "The beacon towers of Leo the Mathematician, built in Theophilos's reign",
        author = "Leo the Mathematician",
        year = 842,
        summary = "A line of beacon towers credited to Leo the Mathematician carried news by fire signal from the eastern frontier to the capital, and the placed year 842 is the death of Emperor Theophilos.",
        details = "This is a placed record: the sources do not date this device, and 842 is the year of the anchor, not of the making. Accounts from the tenth century place the beacon line in the reign of Theophilos, from 829 to 842, and say it was closed once his son Michael III took the throne in 842.",
        rare = true,
    ),

)
