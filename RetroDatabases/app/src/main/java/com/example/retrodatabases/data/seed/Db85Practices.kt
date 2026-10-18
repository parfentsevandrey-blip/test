package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1190 and 1194: a Genoese notary's register of contracts and the carucage that helped pay the ransom of Richard I. */
val db85Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.PRACTICE,
        section = "Genoa",
        title = "Oberto Scriba de Mercato's register of Genoese contracts, 1190 to 1192",
        originalTitle = "Cartularium Oberti Scribe de Mercato",
        author = "Oberto Scriba de Mercato",
        year = 1190,
        summary = "The Genoese notary Oberto Scriba de Mercato kept a register of the contracts he drew up in Genoa from 1190 to 1192.",
        details = "The register records sales, loans and agreements written in Latin, and it is among the earliest surviving registers kept by a notary of Genoa. Its entries are dated by day and year, so historians can follow the work of one notary across the early 1190s.",
        rare = true,
    ),

    entry(
        databaseId = 85,
        kind = Kind.PRACTICE,
        section = "England",
        title = "The carucage of 1194, a tax on plough-land for Richard I's ransom",
        originalTitle = "Carucagium",
        author = "The English government of Richard I",
        year = 1194,
        summary = "In 1194 England levied the carucage, a tax assessed on each plough-land, to help pay the ransom of Richard I after his captivity in Germany.",
        details = "The tax took its name from the carucate, the unit of plough-land used to assess it. It was one of the levies that raised the sum demanded for the king's release, and the carucage is known from the royal accounts and the chronicles.",
    ),

)
