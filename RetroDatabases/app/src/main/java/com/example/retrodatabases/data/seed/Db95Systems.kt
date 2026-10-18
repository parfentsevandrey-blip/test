package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1090 to 1097: the Nizari fortress of Alamut, the synod of Szabolcs, Durham Cathedral, the council of Clermont and the congress of Liubech. */
val db95Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.SYSTEM,
        section = "Persia",
        title = "The Nizari state of Alamut, taken by Hasan-i Sabbah in 1090",
        originalTitle = "Qal'at Alamut",
        author = "Hasan-i Sabbah",
        year = 1090,
        summary = "Hasan-i Sabbah took the mountain fortress of Alamut in northern Persia in 1090, and from it his Nizari Ismaili followers ran a network of castles until the Mongols took the fortress in 1256.",
        details = "Western writers called the Nizaris Assassins, a name whose origin is disputed, and the state was feared by its rivals for its targeted killings. Accounts of its castles and missions come mostly from enemies and later chroniclers, so the details of its organisation remain uncertain.",
    ),

    entry(
        databaseId = 95,
        kind = Kind.SYSTEM,
        section = "Hungary",
        title = "The synod of Szabolcs in Hungary, a church council of May 1092",
        originalTitle = "Szabolcs synod",
        author = "Ladislas I of Hungary and the Hungarian prelates",
        year = 1092,
        summary = "The synod of Szabolcs met in Hungary in May 1092 under King Ladislas I and issued church decrees on clergy, worship and the conduct of Christians.",
        details = "The decrees covered the conduct of clergy and laity, church administration and worship, and one enactment forbade Jews to take Christian wives or to hold Christian slaves. Accounts give 20 or 21 May as the day of the meeting, and the full list of its decrees cannot be given with certainty.",
        rare = true,
    ),

    entry(
        databaseId = 95,
        kind = Kind.SYSTEM,
        section = "Architecture",
        title = "Durham Cathedral, begun in 1093 by Bishop William de St-Calais",
        originalTitle = "Durham Cathedral",
        author = "William de St-Calais",
        year = 1093,
        summary = "Durham Cathedral was begun in 1093 by William de St-Calais, the Norman bishop of Durham, as a massive stone church whose nave was later roofed with ribbed vaults.",
        details = "The building was planned with great piers and round arches, and its ribbed vaults were built from about 1130, which makes it one of the earliest large churches to use them, though historians debate which building came first. The site is a rocky peninsula above the River Wear.",
    ),

    entry(
        databaseId = 95,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The council of Clermont and Urban II's call to the First Crusade, 1095",
        originalTitle = "Concilium Claromontanum",
        author = "Pope Urban II",
        year = 1095,
        summary = "Pope Urban II called for an armed expedition to the East at the council of Clermont, and his speech of 27 November 1095 is the usual date given for the call.",
        details = "Several later writers give versions of the speech, and their wording differs, so the text of the call is known only in reconstructions. The council also issued church decrees, and the crusade it set in motion reached Jerusalem in 1099.",
    ),

    entry(
        databaseId = 95,
        kind = Kind.SYSTEM,
        section = "Rus",
        title = "The congress of Liubech, where the princes of Rus agreed that each keeps his patrimony",
        originalTitle = "Lyubechsky sezd",
        author = "Vladimir Monomakh and the princes of Rus",
        year = 1097,
        summary = "In 1097 the princes of Rus met at Liubech and agreed that each prince should hold the patrimony of his own branch, which ended the shared holding of the family land.",
        details = "Monomakh is credited with convening the meeting, and the agreement aimed to unite the princes against the Polovtsy nomads. Some accounts date the congress to 19 October 1097, and the lists of princes present differ between accounts.",
        rare = true,
    ),

)
