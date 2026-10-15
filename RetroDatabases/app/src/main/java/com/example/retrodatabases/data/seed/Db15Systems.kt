package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1890 to 1900: punched-card tabulation, step-by-step switching, coded typesetting and early telephone services. */
val db15Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.SYSTEM,
        section = "Census",
        title = "Hollerith's tabulating system for the 1890 census",
        originalTitle = "The Art of Compiling Statistics, US patent 395,782 (1889)",
        author = "Herman Hollerith",
        year = 1890,
        summary = "Punched cards were read by electrical contacts and sorted and counted by machine, so the 1890 United States census could be tabulated far faster than before.",
        details = "Hollerith's 1889 patent described the card reader and the tabulator, and the 1890 census used the system. Accounts give the count as finished in about two years, against about seven for the 1880 census.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.SYSTEM,
        section = "Telephone switching",
        title = "Strowger's automatic telephone exchange",
        originalTitle = "Automatic Telephone Exchange, US patent 447,918 (1891)",
        author = "Almon B. Strowger",
        year = 1891,
        summary = "A step-by-step switch let a subscriber dial a number, so calls reached the right line without an operator.",
        details = "The patent was granted in 1891, and the first automatic exchange is dated to La Porte, Indiana, in 1892. Each dialled digit moved the switch one step, which set the pattern of step-by-step exchanges used for decades.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.SYSTEM,
        section = "Typesetting",
        title = "Lanston's Monotype keyboard and caster",
        originalTitle = "Lanston Monotype casting and composing machine",
        author = "Tolbert Lanston",
        year = 1896,
        summary = "A keyboard punched a paper ribbon and a separate caster read the ribbon to cast each letter, so typesetting was driven by coded tape.",
        details = "Lanston's keyboard and paper-ribbon system is dated 1887 in some accounts. The hot-metal casting patent, applied for in 1890 and granted in 1896, gives the record its date. The British company and the sale of patent rights are dated 1897, and some accounts give 1900 for the market launch, so the dates differ.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.SYSTEM,
        section = "Telephony",
        title = "First long-distance telephone line, New York to Chicago",
        originalTitle = "Long-distance circuit between New York and Chicago",
        author = "American Telephone and Telegraph Company",
        year = 1892,
        summary = "A telephone circuit joined New York and Chicago in 1892, the first long-distance voice line between the two cities.",
        details = "The line is usually dated to January 1892. Lines this long lost much of the signal along the wire, a loss that the loading coils of 1899 later reduced.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.SYSTEM,
        section = "Telephone news",
        title = "Telefon Hirmondo, a telephone news service in Budapest",
        originalTitle = "Telefon Hirmondo (1893)",
        author = "Tivadar Puskas",
        year = 1893,
        summary = "A telephone service that let subscribers in Budapest hear news and music over the wire at set times.",
        details = "The service began in Budapest in 1893 and is credited to Tivadar Puskas. It is a rare early case of a telephone network used to broadcast programmes, which is why the record is kept.",
        rare = true,
    ),
)
