package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A device of 1000: the observatory on Mokattam that later accounts give to the astronomer Ibn Yunus, with its equipment disputed. */
val db104Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.DEVICE,
        section = "Egypt",
        title = "The Mokattam observatory said to have been set up for Ibn Yunus near Fustat",
        originalTitle = "Observatory on Mokattam",
        author = "Ibn Yunus and his Fatimid patrons",
        year = 1000,
        summary = "Later accounts say the Fatimids set up an observatory for the astronomer Ibn Yunus on the Mokattam hills near Fustat, but its equipment is poorly documented.",
        details = "The Dictionary of Scientific Biography notes that the well-equipped observatory appears often in popular accounts despite thin evidence. The only reports of large instruments come from about 1200 and about 1290, more than a century after Ibn Yunus died in 1009, so historians disagree on how well equipped the site was.",
        rare = true,
    ),

)
