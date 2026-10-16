package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1370 to 1377: a Paris tower clock, a Barcelona astrolabe of 1375, Ibn al-Shatir's minaret sundial, the Jikji type of 1377 and a Ming bronze gun. */
val db67Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Henri de Vic's tower clock for the Palais de la Cite in Paris",
        originalTitle = "Horloge du Palais",
        author = "Henri de Vic",
        year = 1370,
        summary = "Henri de Vic built a tower clock for King Charles V of France at the Palais de la Cite in Paris, commissioned in 1370.",
        details = "One account dates the installation to 1371 and another puts the fitting itself in 1370. Accounts also differ on where Henri de Vic came from, with some calling him a German engineer and others a man from Lorraine, and the mechanism still runs after a restoration in 2012.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.DEVICE,
        section = "Astrolabes",
        title = "A gilded brass astrolabe made at Barcelona, dated 1375",
        originalTitle = "Astrolabe",
        author = "An unnamed maker of Barcelona",
        year = 1375,
        summary = "A gilded brass astrolabe made at Barcelona bears the date 1375 and is held by the Museum of Fine Arts in Boston.",
        details = "The instrument is signed and dated, which makes it useful for the early history of European astrolabes, and the museum counts it among the oldest surviving European examples. Catalogues disagree about which dated European astrolabe is the earliest, so its place in that list depends on the comparison made.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.DEVICE,
        section = "Sundials",
        title = "Ibn al-Shatir's sundial on the minaret of the Umayyad Mosque in Damascus",
        originalTitle = "Minaret of the Bride sundial",
        author = "Ibn al-Shatir",
        year = 1371,
        summary = "Ibn al-Shatir's marble sundial on the minaret of the Umayyad Mosque at Damascus marked the equal hours of prayer, and reference pages date it to 1371 or 1372.",
        details = "The engraved marble slab was about two metres by one, and its scale was meant for the equal hours of prayer. Accounts differ on the replacement that stands on the minaret today, and the original dial is said to have gone to a museum in the city.",
        rare = true,
    ),

    entry(
        databaseId = 67,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Jikji, the oldest surviving book printed with movable metal type",
        originalTitle = "Jikji",
        author = "Buddhist monks of Heungdeok Temple, Cheongju",
        year = 1377,
        summary = "The Jikji was printed in 1377 at Heungdeok Temple in Cheongju with movable metal type, and it is the oldest surviving book printed that way.",
        details = "The surviving volume is the second part, now kept in the National Library of France in Paris, and its colophon gives the date 1377. The book collects Zen teachings, and its printing came almost eighty years before the Gutenberg Bible.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.DEVICE,
        section = "Guns",
        title = "The Great Bowl-Mouth Tube, a small bronze gun of the Ming, dated 1372",
        originalTitle = "Great Bowl-Mouth Tube",
        author = "Ming artillery makers",
        year = 1372,
        summary = "The Great Bowl-Mouth Tube is a small bronze gun of the Ming dynasty, weighing about sixteen kilograms, which surveys of Ming artillery date to 1372.",
        details = "The same survey gives the gun a length of 36.5 centimeters, and the sources do not say whether the date is cut into the metal or comes from other evidence. The year is therefore given as the date recorded in those surveys.",
        rare = true,
    ),

)
