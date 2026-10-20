package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 250: the libelli of the Decian sacrifice, certificates that proved a sacrifice had been made. */
val db179Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.PRACTICE,
        section = "Sacrifice",
        title = "The libelli of the Decian sacrifice, certificates of 250",
        originalTitle = "Libelli",
        author = "The local commissions of the Decian edict",
        year = 250,
        summary = "Under Decius's edict of 250, people who sacrificed to the gods received a libellus, a certificate that proved the sacrifice had been made.",
        details = "Papyri from Egypt preserve some of these certificates, and accounts differ on how many are known, with totals of 44 and 46 given. Most of the surviving certificates are tied to the edict of 250, and the practice shows how the persecution was recorded on paper.",
        rare = true,
    ),

)
