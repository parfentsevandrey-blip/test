package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 100 to 110: Nicomachus's Introduction to Arithmetic, a Greek schoolbook of number written about 100. */
val db194Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Nicomachus's Introduction to Arithmetic, a Greek schoolbook of number",
        originalTitle = "Arithmetike eisagoge",
        author = "Nicomachus of Gerasa",
        year = 100,
        summary = "Nicomachus of Gerasa wrote the Introduction to Arithmetic in Greek, a short textbook of number that was used in schools for centuries.",
        details = "Nicomachus is usually given a floruit of about 100 CE, and other estimates place his life from about 60 to about 120, so 100 is the year used here. The book is his only surviving mathematical work, and it was later put into Latin by Apuleius and by Boethius.",
        rare = true,
    ),

)
