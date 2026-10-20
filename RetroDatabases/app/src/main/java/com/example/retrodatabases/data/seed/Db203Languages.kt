package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 10 to 20: Augustus's Res Gestae, the emperor's account of his deeds, cut on bronze at Rome and copied in the provinces. */
val db203Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Augustus's Res Gestae, the emperor's account of his deeds cut on bronze at Rome",
        originalTitle = "Res Gestae Divi Augusti",
        author = "Augustus",
        year = 14,
        summary = "Augustus's Res Gestae is his own account of his deeds, written to be displayed on bronze at Rome after his death in 14 and copied in the provinces.",
        details = "Augustus asked that the text be cut on two bronze pillars at Rome, and the bronze original is lost. The most complete copy, at Ancyra in modern Turkey, gives the Latin text with a Greek version beside it, and other copies come from Apollonia and Antioch in Pisidia. The year 14 is the year of his death and the date given for the first public form of the text.",
    ),

)
