package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 690 to 700: a gold pectoral cross linked to St Cuthbert, placed at 698, the year of his translation, because the sources give it no date. */
val db135Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.DEVICE,
        section = "English",
        title = "The gold pectoral cross linked to St Cuthbert, an Anglo-Saxon object usually dated to the late seventh or early eighth century",
        originalTitle = "pectoral cross of Cuthbert",
        author = "Anglo-Saxon goldsmiths",
        year = 698,
        summary = "A gold pectoral cross linked to St Cuthbert is an Anglo-Saxon object that is usually dated to the late seventh or early eighth century.",
        details = "The sources do not date this cross to a year, so 698, when Cuthbert's body was moved into a new coffin at Lindisfarne, is the year of the anchor and not the year of the making. Its link to Cuthbert rests on the tradition of his burial.",
        rare = true,
    ),

)
