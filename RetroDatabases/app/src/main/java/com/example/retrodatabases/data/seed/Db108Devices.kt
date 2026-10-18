package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 960 to 970: the mechanical throne of the Byzantine court, as an envoy of Otto I described it. */
val db108Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.DEVICE,
        section = "Constantinople",
        title = "The mechanical throne of the Byzantine emperors, as Liutprand of Cremona described it on his embassy of 968",
        originalTitle = "Relatio de legatione Constantinopolitana",
        author = "Liutprand of Cremona",
        year = 968,
        summary = "Liutprand of Cremona's report on his embassy of 968 describes the throne hall of the Byzantine emperor, where mechanical lions roared and the throne was raised.",
        details = "Liutprand came to Constantinople as an envoy of Otto I in 968 and was received by the emperor Nikephoros II Phokas, and his report of the embassy is the source for the court machines. An earlier passage in his Antapodosis describes the same hall from a visit of 949, so the device appears in two of his accounts. The text gives no working details, so the mechanism cannot be rebuilt from it, and Liutprand wrote as a critical envoy, which is one reason historians read the spectacle with care.",
        rare = true,
    ),

)
