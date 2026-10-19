package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 760 to 770: the Hyakumanto darani, a million printed charms ordered in 764. */
val db128Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "The Hyakumanto darani, one million miniature pagodas each holding a printed charm, ordered in 764",
        originalTitle = "Hyakumanto Darani",
        author = "Empress Shotoku",
        year = 764,
        summary = "Empress Shotoku ordered in 764 a million miniature pagodas, each holding a printed Buddhist charm.",
        details = "The order is linked to thanks for the defeat of the Fujiwara no Nakamaro rebellion in 764, and the pagodas were sent to ten leading temples. The work is usually given as finished around 770. Accounts differ on whether the charms were printed from wood blocks or from metal plates.",
    ),

)
