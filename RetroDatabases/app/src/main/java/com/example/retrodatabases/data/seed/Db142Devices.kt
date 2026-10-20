package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 620 to 630: the Sutton Hoo lyre, undated and placed at 624, the year of the anchor. */
val db142Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.DEVICE,
        section = "Anglo-Saxon",
        title = "The Sutton Hoo lyre from the ship burial, placed at 624 with the usual date of King Raedwald's death",
        originalTitle = "Sutton Hoo lyre",
        author = "Unknown maker",
        year = 624,
        summary = "The lyre from the Sutton Hoo ship burial is known from its metal fittings and modern rebuilds, and it is placed at 624, the year usually given for King Raedwald's death.",
        details = "The sources do not date this lyre: the ship burial is dated only to the early 7th century and commonly linked to Raedwald, so 624 is the year of the anchor and not the year of the making. Only the two metal escutcheons are original, and the rebuilt forms rest on those fittings and on comparison with other lyres.",
        rare = true,
    ),

)
