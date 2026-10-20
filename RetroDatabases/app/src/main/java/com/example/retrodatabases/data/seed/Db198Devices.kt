package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 60 to 69: Hero's dioptra, a sighting tube on a stand for levels and angles, placed at its eclipse year. */
val db198Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "Hero's dioptra, a sighting tube on a stand for levels and angles",
        originalTitle = "Dioptra",
        author = "Hero of Alexandria",
        year = 62,
        summary = "Hero's dioptra was a sighting tube with a sight at each end, set on a stand to read levels and angles on works such as aqueducts.",
        details = "Hero's date is not fixed, and reference works place him in the 1st or 2nd century CE. His book describes a lunar eclipse observed at Alexandria on 13 March 62 in its chapter 35, which some scholars use to date him, while a critique argues the eclipse may have been invented as an example. The sources do not date the instrument or the book, so 62 is the placed year, the year of this anchor, and not the year of the making.",
        rare = true,
    ),

)
