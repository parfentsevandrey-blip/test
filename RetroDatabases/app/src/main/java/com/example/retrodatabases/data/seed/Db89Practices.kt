package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1154 and 1155: a Genoese notary's register of contracts and the first roll of Henry II's Pipe Roll accounts. */
val db89Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.PRACTICE,
        section = "Notaries",
        title = "Giovanni Scriba's Genoese cartulary, the oldest surviving notarial register",
        originalTitle = "Cartulario di Giovanni Scriba",
        author = "Giovanni Scriba",
        year = 1154,
        summary = "The Genoese notary Giovanni Scriba kept a register of contracts from 1154, and it is the oldest surviving notarial register of the city.",
        details = "Scriba recorded the contracts he drew up, including sea loans, sales and partnerships, in the short notes that Genoese notaries used. The register runs to about 1164, and it is the earliest of its kind to survive from the city.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.PRACTICE,
        section = "Accounts",
        title = "The Pipe Roll of 1155, the first of Henry II's yearly accounts",
        originalTitle = "Pipe Roll",
        author = "The English Exchequer",
        year = 1155,
        summary = "The Pipe Roll of 1155 to 1156 begins the unbroken series of yearly accounts that the English Exchequer kept on rolls of parchment.",
        details = "Each roll recorded the sums that the sheriffs owed to the crown and paid in for the year, and the series runs on from this roll for centuries. The year 1155 is the usual date for the first roll of the unbroken series.",
    ),

)
