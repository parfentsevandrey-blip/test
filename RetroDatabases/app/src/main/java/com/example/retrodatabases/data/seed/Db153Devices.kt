package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 510 to 520: the water clock among the timekeepers that Theoderic sent to the Burgundian king Gundobad, as the Variae record. */
val db153Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.DEVICE,
        section = "Ostrogothic",
        title = "A water clock among the timekeepers that Theoderic sent to the Burgundian king Gundobad, as Cassiodorus's Variae records",
        originalTitle = "horologium",
        author = "Cassiodorus, writing for Theoderic",
        year = 511,
        summary = "Cassiodorus's letters record that Theoderic sent the Burgundian king Gundobad two timekeepers with their operators, one of them apparently a water clock.",
        details = "The letters are dated by their content to about 507 to 511, and this record uses 511, the end of that range, although some online headings date them to about 522, which would fall outside this decade. Both devices are called horologia, a word that covers any timekeeper, and the same letters say that Gundobad had asked for a water clock, a sundial and craftsmen. This record does not say whether the clock arrived or how it worked.",
        rare = true,
    ),

)
