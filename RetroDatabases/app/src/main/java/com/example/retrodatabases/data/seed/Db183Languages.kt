package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 210 to 220: Ulpian's Rules, a Roman law handbook placed at 212 because the sources do not date it. */
val db183Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.LANGUAGE,
        section = "Roman law",
        title = "Ulpian's Rules, a short Latin handbook of Roman law",
        originalTitle = "Liber singularis regularum",
        author = "Ulpian (Domitius Ulpianus)",
        year = 212,
        summary = "Ulpian's Rules is a short Latin handbook of Roman law set out as rules, by a jurist who died in the 220s.",
        details = "The sources do not date this book; 212 is the year of the anchor, the execution of the jurist Papinian by Caracalla, not the year of the book. Ulpian died in 223 in some accounts and in 228 in others.",
        rare = true,
    ),

)
