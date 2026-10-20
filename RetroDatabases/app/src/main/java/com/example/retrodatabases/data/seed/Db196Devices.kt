package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 80: the sinan, a south-pointing ladle that Wang Chong describes, placed at the year of his book. */
val db196Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.DEVICE,
        section = "Magnetism",
        title = "The sinan, a south-pointing ladle that Wang Chong describes",
        originalTitle = "Sinan",
        author = "Unknown makers, described by Wang Chong",
        year = 80,
        summary = "The sinan is a ladle, possibly of lodestone, whose handle points south when it is set down on the ground, and it is one of the earliest reports of a pointing device in China.",
        details = "The sources do not date the ladle itself, so 80 is the year of Wang Chong's account, the placed year for this entry, and not the year of the making. Scholars dispute whether the ladle worked as a compass, and attempts to rebuild it have not clearly shown the effect.",
        rare = true,
    ),

)
