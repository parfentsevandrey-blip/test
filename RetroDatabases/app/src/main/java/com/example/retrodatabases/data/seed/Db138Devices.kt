package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 660 to 670: the water clock at Asuka that the Nihon Shoki dates to 660. */
val db138Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.DEVICE,
        section = "Japanese",
        title = "The water clock at Asuka, the first in Japan according to the Nihon Shoki, set up under Empress Saimei in 660",
        originalTitle = "rokoku",
        author = "The court of Empress Saimei",
        year = 660,
        summary = "The Nihon Shoki dates Japan's first water clock, a rokoku, to 660 under Empress Saimei, and remains at Asuka are taken to be its site.",
        details = "Naka-no-Oe, later Emperor Tenji, was crown prince at the time. Excavation at the Mizuochi site in Asuka from 1981 onward is taken to match the chronicle's description, so the identification rests on digs as well as on the text.",
        rare = true,
    ),

)
