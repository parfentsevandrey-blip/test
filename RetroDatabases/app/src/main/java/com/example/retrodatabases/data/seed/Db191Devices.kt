package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 130 to 140: Zhang Heng's bronze seismoscope, traditionally dated to 132, with eight dragons and eight toads. */
val db191Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.DEVICE,
        section = "Seismology",
        title = "Zhang Heng's seismoscope, a bronze vessel whose falling ball showed the direction of a distant earthquake",
        originalTitle = "Houfeng didong yi",
        author = "Zhang Heng",
        year = 132,
        summary = "Zhang Heng's bronze seismoscope, traditionally dated to 132, let a ball drop from one of eight dragons to show the direction of a distant earthquake.",
        details = "The Hou Hanshu, written in the fifth century, describes a jar-shaped vessel with eight dragons, each holding a bronze ball, and a toad below each dragon to catch the ball. The year 132 is traditional, since no dated example survives, and modern accounts date the Longxi quake that the vessel is said to have shown to 134 or 138. Modern reconstructions disagree about how the mechanism worked.",
    ),

)
