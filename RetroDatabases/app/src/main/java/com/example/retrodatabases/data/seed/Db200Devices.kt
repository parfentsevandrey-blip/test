package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 40: Caligula's tower at Gesoriacum, built to guide ships in the Channel, with its date and identity disputed. */
val db200Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.DEVICE,
        section = "Lighthouses",
        title = "Caligula's lighthouse at Gesoriacum, a tower to guide ships in the Channel",
        originalTitle = "Gesoriacum lighthouse",
        author = "Caligula",
        year = 40,
        summary = "Caligula is credited with a tower at Gesoriacum, the Roman port of modern Boulogne, built to guide ships, and it is dated here to 40.",
        details = "The evidence for the tower rests on one passage of Suetonius's life of Caligula, and the remains at Boulogne called the Tour d'Odre are usually identified with it. The date is disputed: popular accounts give 39 or 40, a proposal moves the monument into the reigns of Trajan and Hadrian, and a 2015 article defends Caligula's authorship.",
        rare = true,
    ),

)
