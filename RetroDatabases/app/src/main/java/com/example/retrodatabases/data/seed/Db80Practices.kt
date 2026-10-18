package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1240 to 1250: the papal registers of Innocent IV and the question-and-article form of the Summa of Alexander of Hales. */
val db80Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.PRACTICE,
        section = "Chancery",
        title = "The letters of Innocent IV, copied into papal registers from 1243",
        originalTitle = "Registra Innocentii IV",
        author = "The papal chancery of Innocent IV",
        year = 1243,
        summary = "The papal chancery copied the letters of Innocent IV into registers from the start of his pontificate on 25 June 1243.",
        details = "Later editors printed the registers from manuscripts kept in the Vatican and in Paris, and the printed editions give conflicting dates. The copies show a chancery keeping its own record of outgoing letters, a practice that the registers of earlier popes had also followed.",
        rare = true,
    ),

    entry(
        databaseId = 80,
        kind = Kind.PRACTICE,
        section = "Scholarship",
        title = "The question-and-article form of the Summa of Alexander of Hales",
        originalTitle = "Summa Halensis",
        author = "Alexander of Hales and the Franciscan masters of Paris",
        year = 1245,
        summary = "The Summa of Alexander of Hales, compiled by Franciscan masters at Paris in the 1240s, sets out theology as questions, each divided into articles with objections and replies.",
        details = "Alexander of Hales died in 1245, and the Summa was finished partly after his death, so its date is given as the 1240s to the 1250s, with accounts differing on how much he wrote himself. The question and article form became the usual way of arranging university theology in the decades that followed.",
        rare = true,
    ),

)
