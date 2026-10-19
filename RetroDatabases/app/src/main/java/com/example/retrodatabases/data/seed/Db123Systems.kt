package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 810 to 820: the Abbasid civil war of 811 to 813, Leo V's icon-breaking of 815, Louis the Pious's Ordinatio of 817. */
val db123Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.SYSTEM,
        section = "Abbasid",
        title = "The civil war between the Abbasid brothers al-Amin and al-Ma'mun, 811 to 813",
        originalTitle = "Fitna of al-Amin and al-Ma'mun",
        author = "Al-Amin and al-Ma'mun, with their generals Tahir ibn Husayn and Harthama ibn A'yan",
        year = 811,
        summary = "The civil war between the Abbasid brothers al-Amin and al-Ma'mun began in 811 and ended with al-Amin's death in Baghdad in 813.",
        details = "Al-Amin, who had succeeded their father Harun al-Rashid in 809, tried to pass the succession to his own son and so set himself against al-Ma'mun. Tahir ibn Husayn won at Rayy in 811, and the siege of Baghdad ran from August 812 until late September 813, when al-Amin was seized and killed that same night.",
    ),

    entry(
        databaseId = 123,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Leo V the Armenian restores icon-breaking at a Constantinople council of 815",
        originalTitle = "Leo V the Armenian restores icon-breaking at a Constantinople council of 815",
        author = "Leo V the Armenian",
        year = 815,
        summary = "In 815 Emperor Leo V revived the official ban on icons, which the Second Council of Nicaea had lifted in 787.",
        details = "The council at Constantinople declared the veneration of images unlawful, and the second period of icon-breaking lasted until 843, when the restoration of images was celebrated as the Triumph of Orthodoxy.",
    ),

    entry(
        databaseId = 123,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "Louis the Pious's Ordinatio imperii of 817, the plan that made Lothair co-emperor",
        originalTitle = "Ordinatio imperii",
        author = "Louis the Pious",
        year = 817,
        summary = "Louis the Pious issued the Ordinatio imperii in 817, a succession plan that named his eldest surviving son Lothair co-emperor and gave kingdoms to his younger sons.",
        details = "Lothair was to rule the empire as its senior emperor, while Pepin received Aquitaine and Louis the German received Bavaria, and the plan was revised several times in the 820s and 830s. It did not hold, and the empire was divided at Verdun in 843.",
    ),

)
