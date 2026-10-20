package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Language of 110 to 120: Suetonius's book on grammarians and rhetoricians, placed at 117 for the accession of Hadrian. */
val db193Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Suetonius's book on grammarians and rhetoricians",
        originalTitle = "De grammaticis et rhetoribus",
        author = "Suetonius",
        year = 117,
        summary = "Suetonius wrote a short Latin book of lives of teachers of grammar and rhetoric, which is undated, so the year 117 is a placed year.",
        details = "Suetonius later served Hadrian as secretary, and the book survives only in part. The sources do not date this book; 117 is the year Hadrian came to power, the anchor for the placed year, not the year of writing.",
        rare = true,
    ),

)
