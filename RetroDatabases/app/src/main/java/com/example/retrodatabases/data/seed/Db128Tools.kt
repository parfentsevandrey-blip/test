package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 760 to 770: the mokkan wooden tablets of the Nara palace, placed at 764 by the anchor of Empress Shotoku's second reign. */
val db128Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.TOOL,
        section = "Japanese",
        title = "The mokkan, wooden message tablets for notes and labels at the Nara palace",
        originalTitle = "mokkan",
        author = "Officials of the Nara palace",
        year = 764,
        summary = "Mokkan are thin wooden tablets on which Nara palace officials wrote notes and labels in ink.",
        details = "No single year is given for this practice, and 764 is the year of the anchor, the start of Empress Shotoku's second reign, not the date of any tablet. Tablets of this kind have been found in great numbers in the palace ruins, and the record covers the practice of writing on wood rather than one piece.",
        rare = true,
    ),

)
