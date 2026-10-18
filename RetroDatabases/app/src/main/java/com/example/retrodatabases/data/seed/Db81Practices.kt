package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1231 to 1235: a Paris strike of lectures, a gold coin of Sicily, a court's retreat to an island, a papal inquisition given to friars, and a Mongol council. */
val db81Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.PRACTICE,
        section = "University",
        title = "The Paris strike of 1229 to 1231, ended by the bull Parens scientiarum",
        originalTitle = "Parens scientiarum",
        author = "Pope Gregory IX",
        year = 1231,
        summary = "The masters and students of Paris suspended their lectures for about two years, and the papal bull Parens scientiarum of 1231 gave the masters the right to suspend lectures when their privileges were broken.",
        details = "The dispute began with clashes in Paris in 1229, and many scholars left the city for other towns during the suspension. Accounts differ on the exact course of the quarrel, but the bull of 1231 is the document that ended it and put the university's rights in writing.",
        rare = true,
    ),

    entry(
        databaseId = 81,
        kind = Kind.PRACTICE,
        section = "Coinage",
        title = "The augustale, Frederick II's gold coin of 1231",
        originalTitle = "Augustalis",
        author = "The mints of Frederick II",
        year = 1231,
        summary = "Frederick II issued the augustale in 1231, a gold coin for the kingdom of Sicily that is widely described as the first gold coin struck in western Europe since Carolingian times.",
        details = "The coin took its name from the Augustus, the imperial title Frederick claimed, and it was struck in gold at the kingdom's mints. Accounts give different figures for its weight and purity, so no single standard is quoted here.",
        rare = true,
    ),

    entry(
        databaseId = 81,
        kind = Kind.PRACTICE,
        section = "Mongol war",
        title = "The Goryeo court's move to Ganghwa island in 1232",
        originalTitle = "Ganghwa",
        author = "King Gojong of Goryeo",
        year = 1232,
        summary = "The Goryeo court moved to Ganghwa island in 1232 to resist the Mongol invasions, and it stayed there until 1270.",
        details = "The island lies at the mouth of the Han river near the modern capital, and the government ruled from it while the armies fought on the mainland. The court's retreat is the setting for the recarving of the Tripitaka Koreana, which began in 1236.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.PRACTICE,
        section = "Church courts",
        title = "The papal inquisition in Languedoc handed to the Dominicans in 1233",
        originalTitle = "Ille humani generis",
        author = "Pope Gregory IX",
        year = 1233,
        summary = "In 1233 Pope Gregory IX gave the Dominican friars the work of the inquisition against heresy in Languedoc, in the bull Ille humani generis.",
        details = "The bull is usually dated to April 1233, and it made the friars the papal agents of inquiry in the south of France. Later inquisitions followed the same model of papal inquiry carried out by friars.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.PRACTICE,
        section = "Mongol empire",
        title = "The great kurultai of 1235, the Mongol council that launched the western campaigns",
        originalTitle = "Kurultai",
        author = "Ogedei Khan and the Mongol princes",
        year = 1235,
        summary = "The Mongol princes met in a great kurultai at Karakorum in 1235 and agreed on new campaigns, against the Song in China and in the lands to the west.",
        details = "Batu's expedition into the Volga lands began in 1236, and it was part of the western campaign agreed at this assembly. The decisions are known mainly from later chronicles, so accounts differ on the details of the meeting.",
    ),

)
