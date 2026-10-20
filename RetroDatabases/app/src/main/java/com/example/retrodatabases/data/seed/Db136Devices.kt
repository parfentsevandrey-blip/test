package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 680 to 690: the catapults of the Umayyad army at the siege of Mecca in 683, whose fire damaged the Ka'ba. */
val db136Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.DEVICE,
        section = "Arabic",
        title = "The catapults of the Umayyad siege of Mecca in 683, whose fire damaged the Ka'ba",
        originalTitle = "manjaniq",
        author = "Husayn ibn Numayr and the Umayyad army",
        year = 683,
        summary = "Catapults set up by the Umayyad army against Mecca in 683 threw fire into the town, and the Ka'ba was damaged in the fighting.",
        details = "The siege ran from about late September to late November 683, and news of Yazid's death ended it. Accounts differ on how much of the shrine burned and on whether the siege lasted one month or two.",
        rare = true,
    ),

)
