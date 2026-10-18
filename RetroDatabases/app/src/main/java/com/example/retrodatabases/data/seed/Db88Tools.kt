package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1160 to 1170: Rufinus's Summa on Gratian's Decretum, a handbook for the canon-law schools of Bologna. */
val db88Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.TOOL,
        section = "Canon law",
        title = "Rufinus's Summa on Gratian's Decretum, a canon-law handbook from Bologna",
        originalTitle = "Summa decretorum",
        author = "Rufinus of Bologna",
        year = 1162,
        summary = "Rufinus of Bologna wrote a Summa on Gratian's Decretum, a handbook of canon law for the schools, in the early 1160s.",
        details = "Rufinus taught canon law at Bologna, and his Summa is one of the earliest surviving full commentaries on Gratian's collection of church law. Accounts differ on the exact dates of the Summa, so the early 1160s is an approximate date.",
        rare = true,
    ),

)
