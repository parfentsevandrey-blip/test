package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 770 to 780: Beatus of Liebana's Latin Commentary on the Apocalypse, finished in 776 in the valley of Liebana. */
val db127Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Beatus of Liebana's Commentary on the Apocalypse, a Latin commentary on Revelation finished in 776",
        originalTitle = "Commentarius in Apocalypsin",
        author = "Beatus of Liebana",
        year = 776,
        summary = "Beatus of Liebana's Commentary on the Apocalypse, a Latin reading of the book of Revelation, was finished in the valley of Liebana in northern Spain in 776.",
        details = "The commentary goes through Revelation verse by verse and draws on earlier writers such as Tyconius and Apringius of Beja. Accounts date its completion to 776, and the painted copies that made the work famous are of the tenth century and later.",
        rare = true,
    ),

)
