package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 470 to 480: the wergild, the fixed price of a killed man, a placed record at 475 because the sources do not date the custom. */
val db157Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.PRACTICE,
        section = "Law",
        title = "Wergild, the fixed price of a killed man in Germanic law",
        originalTitle = "wergild",
        author = "The Germanic peoples and their law assemblies",
        year = 475,
        summary = "Germanic law set a fixed price in money for a killed man, the wergild, which was paid to his kin in place of a feud.",
        details = "The sources do not date this custom; 475 is the year of the anchor, Euric's law code, and not the date of the practice. Later Frankish and Burgundian codes fix such prices in gold coins, and the payment went to the kin of the dead.",
    ),

)
