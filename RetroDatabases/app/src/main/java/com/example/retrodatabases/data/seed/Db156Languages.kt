package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 480 to 490: one Latin history of the Vandal persecution in Africa, dated to about 488. */
val db156Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Victor of Vita's History of the Vandal Persecution, a Latin account of the African church",
        originalTitle = "Historia persecutionis Africanae provinciae",
        author = "Victor of Vita",
        year = 488,
        summary = "Victor of Vita wrote a Latin history of the persecution of Catholic bishops and clergy in Africa under the Vandal kings, and it is the main source for that persecution.",
        details = "The work preserves documents from the debate of 484 between Catholic and Arian bishops, including a confession of faith by Eugenius of Carthage. Accounts differ on the date of composition, with about 484 and 488 both given, and 488 is used here.",
        rare = true,
    ),

)
