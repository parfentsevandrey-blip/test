package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1150 to 1160: the Jayrun water clock beside the Umayyad Mosque in Damascus, driven by water through pulleys and ropes. */
val db89Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.DEVICE,
        section = "Water",
        title = "The Jayrun water clock by the Umayyad Mosque in Damascus",
        originalTitle = "Jayrun clock",
        author = "Muhammad ibn al-Sa'ati and his son Ridwan ibn al-Sa'ati",
        year = 1154,
        summary = "A water-driven clock stood beside the Umayyad Mosque in Damascus in the reign of Nur al-Din, and a treatise of 1203 describes its machinery.",
        details = "Muhammad ibn al-Sa'ati is credited with the clock, and his son Ridwan's treatise of 1203 describes how water power drove its machinery through pulleys and ropes. The sources place the clock in Nur al-Din's reign, which runs from 1154 to 1174, so 1154 is the year used here, and the date of the first clock is not fixed.",
        rare = true,
    ),

)
