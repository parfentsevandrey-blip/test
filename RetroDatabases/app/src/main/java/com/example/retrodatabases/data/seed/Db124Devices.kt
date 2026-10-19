package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 800 to 810: the water clock that Harun al-Rashid sent to Charlemagne, recorded in the Frankish annals for 807. */
val db124Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.DEVICE,
        section = "Abbasid",
        title = "The water clock that Harun al-Rashid sent to Charlemagne, recorded in the Frankish annals for 807",
        originalTitle = "horologium",
        author = "Harun al-Rashid, who sent it from Baghdad",
        year = 807,
        summary = "A brass water clock sent from Baghdad by Harun al-Rashid reached Charlemagne's court in 807, and accounts describe it striking the hours with falling balls and twelve brass riders.",
        details = "Accounts describe balls dropped at the hours and twelve riders coming out of openings, and the sources differ on how the openings were made. The drawings of its mechanism are modern reconstructions rather than records from the period.",
    ),

)
