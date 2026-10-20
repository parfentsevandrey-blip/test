package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 200 to 210: an undated Roman clay oil lamp, placed at 203, the year of the dedication of the arch of Septimius Severus. */
val db184Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.DEVICE,
        section = "Lighting",
        title = "The Roman clay oil lamp, a mould-made lamp with a filling hole and a wick nozzle",
        originalTitle = "Lucerna",
        author = "Roman potters and lamp makers",
        year = 203,
        summary = "The Roman clay oil lamp was a mould-made lamp with a filling hole and a wick nozzle, produced in great numbers across the empire.",
        details = "Roman clay lamps were mass-produced in moulds for centuries, and most surviving lamps are dated only by their shape and decoration. The sources do not date this device; 203 is the year of the arch of Septimius Severus in the Forum, dedicated for the emperor's tenth year in power, a documented anchor for this placed record and not the date of any lamp.",
        rare = true,
    ),

)
