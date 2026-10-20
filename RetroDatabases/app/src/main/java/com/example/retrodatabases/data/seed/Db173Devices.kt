package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 310 to 320: the labarum, a military standard that bore the chi-rho monogram. */
val db173Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.DEVICE,
        section = "Standards",
        title = "The labarum, a military standard bearing the chi-rho monogram",
        originalTitle = "Labarum",
        author = "Constantine's army",
        year = 312,
        summary = "The labarum was a military standard that bore the chi-rho monogram, and Eusebius ties its making to the campaign of 312.",
        details = "Lactantius describes only the sign painted on the soldiers' shields before the battle of the Milvian Bridge, while Eusebius, writing later, describes the standard itself. Accounts differ on whether the standard existed in 312.",
        rare = true,
    ),

)
