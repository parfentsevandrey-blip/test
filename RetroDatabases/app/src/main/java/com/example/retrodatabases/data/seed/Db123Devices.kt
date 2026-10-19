package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 810 to 820: a placed record for the dust board of Indian-style reckoning, placed at 813, the year al-Ma'mun became caliph. */
val db123Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.DEVICE,
        section = "Reckoning",
        title = "The dust board, a sand tray on which Indian-style numerals were written and wiped out",
        originalTitle = "The dust board, a sand tray on which Indian-style numerals were written and wiped out",
        author = "Unknown reckoners of the Islamic lands",
        year = 813,
        summary = "The dust board was a tray of sand or dust on which reckoners wrote Indian-style numerals, worked a sum, and wiped the figures away at each step.",
        details = "This is a placed record: the sources do not date the dust board, so 813, the year al-Ma'mun became caliph, is the anchor used for placement and not the date of the device. The board was used with Indian numerals, and a reckoner worked a sum by writing the figures in the sand and wiping them out at each step.",
        rare = true,
    ),

)
