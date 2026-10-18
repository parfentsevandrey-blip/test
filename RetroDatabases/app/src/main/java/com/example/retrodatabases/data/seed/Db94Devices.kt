package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1100: a solar marker in Chaco Canyon, whose date is only loosely known. */
val db94Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.DEVICE,
        section = "Solar markers",
        title = "The Sun Dagger of Fajada Butte, a sunlit marker in Chaco Canyon",
        originalTitle = "Sun Dagger",
        author = "Ancestral Puebloan people of Chaco Canyon",
        year = 1100,
        summary = "The Sun Dagger is a solar marker on Fajada Butte in Chaco Canyon, where a shaft of sunlight falls on a carved spiral at the solstices and equinoxes.",
        details = "Its date is uncertain: the structures on the butte are estimated to fall between the 10th and 13th centuries, and a 1982 paper in Science reported that precise ages were unavailable for the rockfall or the carvings. The site was found in a volunteer rock-art survey in 1977, and 1100 is only one date within that span.",
        rare = true,
    ),

)
