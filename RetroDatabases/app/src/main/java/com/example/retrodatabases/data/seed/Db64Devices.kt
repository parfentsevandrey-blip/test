package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1400 to 1410: Korean bronze type, the Yongle Great Bell, a Paris astrolabe, a verge escapement, kango tallies, a paper hammer, a hand-cannon and the magnetic compass. */
val db64Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Gyemija, the bronze movable type of 1403",
        originalTitle = "Gyemija",
        author = "The court of King Taejong",
        year = 1403,
        summary = "The Gyemija was a set of movable type cast in Korea in 1403 under King Taejong, the first metal type of the Joseon court.",
        details = "Its name comes from gyemi, the sexagenary name of 1403. Accounts differ on whether the type was bronze or copper, and one account gives a first run of 100,000 pieces.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Bells",
        title = "The Yongle Great Bell, a bronze bell of the Ming court",
        originalTitle = "Yongle Great Bell",
        author = "Craftsmen of the Yongle court",
        year = 1406,
        summary = "The Great Bell of the Yongle reign is a large cast-bronze bell made for the Ming court, and accounts date its casting either to 1406 or to about 1420.",
        details = "Accounts differ on the date, so the year given here is the earlier of the two and is not settled. The bell now hangs in Beijing's Dazhong Temple, which was founded in the 1730s, long after the bell was cast.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Astrolabes",
        title = "A Paris astrolabe of about 1400, credited to Jean Fusoris",
        originalTitle = "Astrolabe",
        author = "Jean Fusoris",
        year = 1400,
        summary = "An astrolabe made about 1400 and credited to the Paris instrument maker Jean Fusoris is kept in the Museum of the History of Science at Oxford.",
        details = "The instrument is dated to about 1400 and entered the collection by gift from J. A. Billmeir in 1957. The date is approximate, as is usual for instruments of this age.",
        rare = true,
    ),

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The verge escapement of the church clock, about 1400",
        originalTitle = "Verge escapement",
        author = "Makers of church clocks",
        year = 1400,
        summary = "By about 1400 the verge escapement, with its foliot or balance, was the usual way to control the falling weight of a church clock.",
        details = "The escapement released the drive wheel one tooth at a time, so the going train moved in regular steps. Its inventor is not known, and it stayed in use for centuries until the pendulum clock of 1656 replaced it in precise work.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Trade",
        title = "The kango tallies, split tokens that checked each trading ship",
        originalTitle = "Kango",
        author = "Ashikaga Yoshimitsu's government",
        year = 1404,
        summary = "The kango were split tally tokens that the Japanese and Ming sides used from 1404 to check that a trading ship had the right to enter port.",
        details = "Each side held one half of a split tally, and only a pair of halves that fitted together was admitted. Accounts differ on how strictly the early tallies were checked, and the trade that used them is named for them.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Paper",
        title = "The water-driven stamping hammer of the paper mill, about 1400",
        originalTitle = "Stamping hammer",
        author = "Italian paper makers",
        year = 1400,
        summary = "By about 1400 Italian paper mills beat rags into pulp with heavy hammers driven by water wheels, and the mills of Fabriano were among the most famous.",
        details = "The hammers pounded soaked rags in troughs until they became a pulp that could be drained on wire moulds. Accounts differ on the exact date at which each mill adopted the method, so the year here is a round date for the period.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Gunpowder",
        title = "The hand-cannon, a portable gun in use by about 1400",
        originalTitle = "Hand-cannon",
        author = "Unknown gunsmiths",
        year = 1400,
        summary = "By about 1400 hand-cannons were in use in Europe, small barrels of iron or bronze that a soldier fired by hand, with a touch-hole and a charge of powder.",
        details = "The hand-cannon was a forerunner of the handgun and the musket. Accounts differ on the first place where such guns were used, and the early dates vary.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "The magnetic compass in the Ming fleets, in use by 1405",
        originalTitle = "Magnetic compass",
        author = "Chinese navigators",
        year = 1405,
        summary = "By 1405 the magnetic compass was the standard instrument of Chinese navigators, and the Ming treasure fleets steered by it across open water.",
        details = "The compass had been used at sea in China for centuries, and the fleets of the early Ming relied on it with written charts of coasts and courses. The compass in use in 1405 is a general type, not one fixed instrument, so no single object is named here.",
    ),

)
