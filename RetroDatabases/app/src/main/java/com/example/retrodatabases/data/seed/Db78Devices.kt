package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1260 to 1270: Peter Peregrinus's pivoted magnetic compass, the one device of the decade whose first description can be dated with confidence. */
val db78Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.DEVICE,
        section = "Magnets",
        title = "Peter Peregrinus's pivoted magnetic compass, described in 1269",
        originalTitle = "Epistola de magnete",
        author = "Peter Peregrinus of Maricourt",
        year = 1269,
        summary = "Peter Peregrinus described in 1269 a magnetic needle pivoted in a case over a graduated circle, one of the earliest detailed European accounts of a pivoted compass.",
        details = "The letter was written in the army of Charles of Anjou at the siege of Lucera and is dated 8 August 1269. In it Peregrinus described a spherical lodestone, the lines that run from one pole to the other, and the pivoted needle, so the device and the study of the magnet are described together.",
        rare = true,
    ),

)
