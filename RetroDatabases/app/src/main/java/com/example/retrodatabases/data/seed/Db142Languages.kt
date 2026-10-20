package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 620 to 630: Bhaskara I's commentary on the mathematical verses of Aryabhata's Aryabhatiya, dated 629. */
val db142Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.LANGUAGE,
        section = "Sanskrit",
        title = "Bhaskara I's commentary on the mathematical verses of Aryabhata's Aryabhatiya, written in 629",
        originalTitle = "Aryabhatiyabhashya",
        author = "Bhaskara I",
        year = 629,
        summary = "Bhaskara I's commentary on the mathematical verses of Aryabhata's Aryabhatiya, dated 629, is one of the oldest known Sanskrit prose works on mathematics and astronomy.",
        details = "The commentary stops at the 33 mathematical verses of the Aryabhatiya and leaves the rest of the treatise unexplained. It is dated 629 CE, and Bhaskara I's own dates are only roughly known, so the year belongs to the work and not to the length of its author's life.",
        rare = true,
    ),

)
