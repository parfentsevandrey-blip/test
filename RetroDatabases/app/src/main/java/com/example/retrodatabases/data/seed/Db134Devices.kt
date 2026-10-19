package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 700 to 710: the water clock kept by the Onmyoryo office that the Taiho code set up in 701, placed at that date. */
val db134Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.DEVICE,
        section = "Japanese",
        title = "The water clock of the Onmyoryo, kept by two clepsydra scholars under the Taiho code of 701",
        originalTitle = "rokoku",
        author = "The Onmyoryo office under the Taiho code",
        year = 701,
        summary = "The Onmyoryo office set up by the Taiho code kept a water clock, read by two clepsydra scholars, from which the hours were then announced.",
        details = "The sources do not date this clock; 701 is the year the Taiho code set up the office and its clepsydra scholars, not the year the clock was made. Accounts differ on the code's date, with 701, 702 and 703 all given.",
        rare = true,
    ),

)
