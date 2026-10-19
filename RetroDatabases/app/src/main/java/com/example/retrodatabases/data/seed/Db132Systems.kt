package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Land law of 720 to 730: the Sanze Isshin no Ho of 723, which gave reclaimed fields three generations of tenure. */
val db132Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Sanze Isshin no Ho of 723, a Japanese land rule giving reclaimed fields three generations of tenure",
        originalTitle = "Sanze Isshin no Ho",
        author = "The Japanese court",
        year = 723,
        summary = "The Sanze Isshin no Ho of 723 let people who dug new ditches or ponds keep the fields they reclaimed for three generations, and other fields for one lifetime.",
        details = "Reference works give the fourth month of 723 as its date, and in 743 the Konden Einen Shizai Ho made reclaimed land private property for good. Accounts differ on whether the three generations count the holder himself or only his descendants.",
        rare = true,
    ),

)
