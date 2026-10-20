package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 600 to 610: the water-powered grain mill that the Goguryeo monk Docho built in Japan, recorded under 610. */
val db144Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.DEVICE,
        section = "Japanese",
        title = "The water-powered grinding mill that the Goguryeo monk Docho built in Japan, recorded under 610",
        originalTitle = "nian-ai",
        author = "Docho (Damjing), Goguryeo monk",
        year = 610,
        summary = "The Nihon Shoki records that the Goguryeo monk Docho, who came to Japan in 610, built a water-powered grain mill as well as making paints, paper and ink.",
        details = "The Nihon Shoki, completed in 720, places his arrival in the third month of the eighteenth year of Empress Suiko, which is 610, and gives the mill only a short line. Almost nothing else is recorded about him, so the mill rests on that one entry.",
        rare = true,
    ),

)
