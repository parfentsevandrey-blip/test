package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 330 to 340: Hero's aeolipile, a steam-driven sphere, placed at 337 because the sources do not date it in these years. */
val db171Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.DEVICE,
        section = "Steam",
        title = "Hero's aeolipile, a hollow sphere that spun on jets of steam",
        originalTitle = "aeolipile",
        author = "Hero of Alexandria",
        year = 337,
        summary = "Hero of Alexandria describes the aeolipile, a hollow sphere that spins when steam leaves two bent pipes, and it is placed here at 337.",
        details = "Hero describes the device in his Pneumatics, a work of the first century CE, so the device is much older than this year. The sources do not date the device to the 330s; 337 is the year of the anchor, the death of Constantine, and not the year of the making.",
        rare = true,
    ),

)
