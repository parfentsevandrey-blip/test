package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 860 to 870: the Roda Nilometer at Cairo, a stone gauge of the Nile rebuilt in 861. */
val db118Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.DEVICE,
        section = "Nile",
        title = "The Roda Nilometer in Cairo, a stone gauge of the Nile rebuilt in 861",
        originalTitle = "Miqyas al-Nil",
        author = "Caliph al-Mutawakkil, as the inscription is read",
        year = 861,
        summary = "The Nilometer on Roda Island at Cairo, a stone well with a graduated column for the Nile's level, was rebuilt in 861, and its readings fed into tax assessments.",
        details = "The inscription is dated Rajab 247 of the Hijri era, which is 861, and it is read as naming al-Mutawakkil, although some accounts credit al-Ma'mun instead. Some accounts name al-Farghani as its designer, and the first gauge on the island is usually dated to 715 under the Umayyads.",
        rare = true,
    ),

)
