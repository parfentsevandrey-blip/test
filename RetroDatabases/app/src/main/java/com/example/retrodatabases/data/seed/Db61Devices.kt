package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1430 to 1440: Korean clocks, instruments and a metal type of Sejong's court, and Alberti's veil for drawing in perspective. */
val db61Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Timekeeping",
        title = "Jagyeongnu, the self-striking clock of Korea",
        originalTitle = "Jagyeongnu",
        author = "Jang Yeong-sil and Kim Bin",
        year = 1434,
        summary = "The Jagyeongnu was a water clock that announced each hour on its own with bells, gongs and drums, and it was made for the Seoul court in the 1430s.",
        details = "Most accounts date the original clock to 1434, in the sixteenth regnal year of King Sejong, and credit Jang Yeong-sil with building it. The 1434 clock itself has not survived, and the clock that remains dates from 1536, under King Jungjong, and is kept by the National Palace Museum in Seoul.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The Ilseongjeongsi, an instrument that told time by sun and stars",
        originalTitle = "Ilseong jeongsi",
        author = "Yi Cheon and Jang Yeong-sil",
        year = 1437,
        summary = "The Ilseongjeongsi of 1437 was a Korean instrument that kept time from the sun by day and from star patterns at night.",
        details = "The royal annals of Sejong's reign place the making of the instrument in 1437, and Yi Cheon and Jang Yeong-sil are credited with leading the team. The annals are the main written record of the instrument.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Sundials",
        title = "Angbuilgu, the bowl-shaped sundial of Seoul",
        originalTitle = "Angbuilgu",
        author = "Lee Soon-ji and Jang Yeong-sil",
        year = 1434,
        summary = "The angbuilgu was a bronze sundial with a concave dial plate, a gnomon and a pedestal, and the Joseon court set such sundials out in public places in Seoul.",
        details = "The bowl shape gives a dial plate that is curved toward the sky, which is why the instrument is named after an upturned cauldron. Accounts differ on the year of invention, with dates in the mid-1430s given, and among those credited with devising it are Lee Soon-ji and Jang Yeong-sil.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The Gapinja metal type of 1434",
        originalTitle = "Gapinja",
        author = "The Joseon court under King Sejong",
        year = 1434,
        summary = "The Gapinja type was a set of metal movable types for Chinese characters, linked to the year 1434 during the reign of King Sejong.",
        details = "The name comes from the sexagenary label of the year 1434, when the type was cast. The royal annals of Sejong's reign record that the court ordered the casting, and the type was used for royal printing.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Perspective",
        title = "Alberti's veil, a grid of threads for drawing in perspective",
        originalTitle = "Velum",
        author = "Leon Battista Alberti",
        year = 1435,
        summary = "Alberti's veil was a transparent grid of threads held before the eye, so that a painter could copy the squares of the grid onto a panel and draw in perspective.",
        details = "Alberti described the veil in his treatise on painting of 1435, and he treated it as a simple way to draw a scene that recedes to a single point. The grid let a painter check each square of the scene against the square on the panel.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The honcheonui, a water-driven armillary sphere of 1433",
        originalTitle = "Honcheonui",
        author = "Jang Yeong-sil",
        year = 1433,
        summary = "The honcheonui of 1433 was a Korean armillary sphere, a set of rings that models the heavens around a central globe, and water power was used to turn it so that it showed the time.",
        details = "The honcheonui is dated to 1433 and credited to Jang Yeong-sil, and it belongs to the same reign as the Jagyeongnu and the Angbuilgu. Its inner globe was turned by water power, which let the instrument show the motion of the heavens.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The ganui, a simplified armillary first made of wood in 1432",
        originalTitle = "Ganui",
        author = "The Joseon court under King Sejong",
        year = 1432,
        summary = "The ganui was a simplified armillary instrument of Sejong's court, and a wooden one of 1432 was set up to help find the latitude of Seoul.",
        details = "A bronze ganui was made later, and its date is less certain than that of the wooden one. The annals of Sejong's reign record the observatory platform built for the ganui, which should not be confused with the instrument itself.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The honsang, a Joseon celestial globe of 1435",
        originalTitle = "Honsang",
        author = "The Joseon court under King Sejong",
        year = 1435,
        summary = "The honsang was a celestial globe made under King Sejong, a sphere that showed the stars and could be turned to match the sky.",
        details = "The honsang is dated to 1435, and it belongs to the same group of instruments as the honcheonui and the ganui. The court had these instruments made within the same decade.",
    ),

)
