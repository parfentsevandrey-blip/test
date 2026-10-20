package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 570 to 580: the steelyard, a lever scale with a sliding weight, in the form of the Roman statera. */
val db147Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.DEVICE,
        section = "Roman and Byzantine",
        title = "The steelyard, a lever scale with a sliding weight in the form of the Roman statera",
        originalTitle = "statera",
        author = "Unknown Roman and Byzantine makers",
        year = 578,
        summary = "The steelyard weighs a load by balancing it against a sliding weight on a graduated arm, the form the Romans called the statera.",
        details = "No source dates a steelyard to the 570s, so 578, the year Justin II died and Tiberius II took the throne, is a placed year and not the date of the device. The load times its short arm equals the sliding weight times its distance along the long arm, so one weight can read many different loads.",
        rare = true,
    ),

)
