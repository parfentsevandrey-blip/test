package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 390 to 397: Ammianus Marcellinus's Latin history and Rufinus's Latin version of Origen's On First Principles. */
val db165Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Ammianus Marcellinus's Res Gestae, a Latin history from 96 to 378",
        originalTitle = "Rerum gestarum libri XXXI",
        author = "Ammianus Marcellinus",
        year = 390,
        summary = "Ammianus Marcellinus wrote his Latin history in thirty-one books, and the surviving books 14 to 31 cover the years 353 to 378.",
        details = "The history runs from the accession of Nerva in 96 to the death of Valens at Adrianople in 378, but its first thirteen books are lost. Scholars date the writing to the 380s and early 390s, so 390 is a round date inside that span, and accounts differ on when the final books appeared.",
        rare = true,
    ),

    entry(
        databaseId = 165,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Rufinus's Latin version of Origen's On First Principles, released at Rome",
        originalTitle = "De principiis",
        author = "Rufinus of Aquileia",
        year = 397,
        summary = "Rufinus of Aquileia put Origen's Greek treatise On First Principles into Latin and released it at Rome in about 397, with a preface that cast Jerome as an admirer of Origen.",
        details = "The release is dated to just after 397, so that year is used here as an approximation. The preface opened a bitter quarrel between Rufinus and Jerome, and the Greek original is largely lost, so Rufinus's Latin is the main witness to the text.",
        rare = true,
    ),

)
