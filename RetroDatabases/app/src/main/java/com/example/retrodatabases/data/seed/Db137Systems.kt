package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 670 to 680: the Kogo household register of 670, the Synod of Hertford, the Eleventh Council of Toledo and the Arab siege of Constantinople. */
val db137Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Kogo household register of 670, Japan's nationwide register of households, of which no copy survives",
        originalTitle = "Kogo nenjaku",
        author = "Emperor Tenji's government",
        year = 670,
        summary = "The Kogo household register was a nationwide register of Japanese households, compiled in 670 under Emperor Tenji for land, taxes and the levy of men.",
        details = "The register served to allot land, to tax households and to levy men for service, and no copy of it survives. The household registers of 702 are the earliest surviving ones, so this register is older but lost.",
        rare = true,
    ),

    entry(
        databaseId = 137,
        kind = Kind.SYSTEM,
        section = "English",
        title = "The Synod of Hertford in 672, which issued ten decrees for the English church under Theodore",
        originalTitle = "Synod of Hertford",
        author = "Theodore of Tarsus, archbishop of Canterbury",
        year = 672,
        summary = "A synod of the English church at Hertford, presided over by Archbishop Theodore, issued ten decrees on Easter, the conduct of bishops and clergy, and marriage.",
        details = "The council's own dating points to 672, while Bede gives 673, and this record uses 672. Bede lists ten decrees, the first of which concerns the date of Easter and the last of which allows divorce only for adultery.",
        rare = true,
    ),

    entry(
        databaseId = 137,
        kind = Kind.SYSTEM,
        section = "Visigothic",
        title = "The Eleventh Council of Toledo of 675, held under King Wamba of the Visigoths",
        originalTitle = "Concilium Toletanum XI",
        author = "The bishops of the Visigothic kingdom",
        year = 675,
        summary = "The Eleventh Council of Toledo met in 675 under King Wamba, and it is noted for the creed that it issued.",
        details = "Accounts give its opening on 7 November 675, with seventeen bishops, two deacons and five abbots present. It is one of the councils of Toledo, a series held in the Visigothic kingdom from the late sixth century.",
        rare = true,
    ),

    entry(
        databaseId = 137,
        kind = Kind.SYSTEM,
        section = "Umayyad",
        title = "The Arab siege of Constantinople, 674 to 678, which ended in a truce after the Arab fleet was lost",
        originalTitle = "The siege of Constantinople",
        author = "The Umayyad caliphate under Muawiya",
        year = 674,
        summary = "The Umayyad siege of Constantinople, from 674 to 678, ended in a truce after the Arab fleet was destroyed off the city.",
        details = "The caliph Muawiya sent armies and fleets against the Byzantine capital, and the Byzantines are said to have burned the Arab ships with Greek fire. The sources give the truce that ended the siege as lasting thirty years.",
    ),

)
