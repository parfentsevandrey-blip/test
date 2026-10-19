package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 710 to 720, placed at 715 for the death of the caliph Walid I: the water-driven grain mill, which the sources do not date in the Umayyad lands. */
val db133Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.DEVICE,
        section = "Umayyad",
        title = "The water-driven grain mill, undated in the Umayyad lands and placed at 715 for the death of Walid I",
        originalTitle = "Water-driven grain mill",
        author = "Unknown millwrights",
        year = 715,
        summary = "A water-driven grain mill, a wheel turned by a stream to grind corn, is not dated in the sources for the Umayyad lands, and 715 is placed here as the year of the death of the caliph Walid I.",
        details = "The sources do not date the spread of watermills in the Umayyad lands, so 715 is the year of the anchor, the death of Walid I, and not the year a mill was first built. Watermills were known to the Roman world, and the record names no particular mill, miller or place.",
        rare = true,
    ),

)
