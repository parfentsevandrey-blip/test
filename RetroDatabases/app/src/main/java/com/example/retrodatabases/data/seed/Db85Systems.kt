package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1190 to 1198: the bailiffs of Philip Augustus, an English eyre, the Choe military regime in Goryeo Korea, the Armenian kingdom of Cilicia and an English justiciarship. */
val db85Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.SYSTEM,
        section = "France",
        title = "The bailiffs of Philip Augustus, first recorded in 1190",
        originalTitle = "Baillis",
        author = "Philip Augustus of France",
        year = 1190,
        summary = "Philip Augustus of France set up royal bailiffs, the baillis, to supervise the provosts and royal revenues, and the office is first recorded in 1190.",
        details = "Bailiffs could be dismissed by the king and were placed over the local provosts, who had proved unreliable in running the royal estates. Historians usually date the first baillis to 1190, shortly before Philip left on the Third Crusade, but the exact terms of the early ordinance are less clear than the date.",
    ),

    entry(
        databaseId = 85,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The general eyre of 1194, royal justices sent round the counties",
        originalTitle = "Eyre",
        author = "The government of Richard I",
        year = 1194,
        summary = "In 1194 the English government sent royal justices on a general eyre through the counties, to hear pleas, fine offenders and raise money for the crown.",
        details = "An eyre was a judicial circuit in which royal justices sat in each county to hear pleas, and the 1194 eyre is known as a general one that covered the whole country. Its records are partial, so historians rebuild the circuit from the surviving plea rolls and the notices in the chronicles.",
    ),

    entry(
        databaseId = 85,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "The Choe military regime that ruled Goryeo Korea from 1196",
        originalTitle = "Choe-ga jeonggwon",
        author = "Choe Chung-heon",
        year = 1196,
        summary = "Choe Chung-heon seized power in Goryeo Korea in 1196, and his family ruled the kingdom as military dictators until 1258.",
        details = "Military men had controlled the Goryeo court since a coup of 1170, and Choe Chung-heon's killing of the military ruler Yi Ui-min in 1196 began the Choe rule, the best known of these phases. The Choe house lasted until 1258, when its last head was killed in a palace coup.",
    ),

    entry(
        databaseId = 85,
        kind = Kind.SYSTEM,
        section = "Armenia",
        title = "The kingdom of Armenian Cilicia, crowned in 1198 under Leo I",
        originalTitle = "Lesser Armenia",
        author = "Leo I of Armenia",
        year = 1198,
        summary = "Leo I was crowned king of the Armenian principality of Cilicia in 1198, founding a kingdom that lasted until 1375 on the coast of southern Anatolia.",
        details = "Leo had built up the Armenian lordship in the Taurus mountains and the Cilician plain before his coronation. The kingdom is usually dated from its coronation in 1198, and it ended when the Mamluks of Egypt took its last capital, Sis, in 1375.",
        rare = true,
    ),

    entry(
        databaseId = 85,
        kind = Kind.SYSTEM,
        section = "England",
        title = "Hubert Walter's office of chief justiciar, from 1193",
        originalTitle = "Justiciarius",
        author = "Hubert Walter",
        year = 1193,
        summary = "Hubert Walter became chief justiciar of England in 1193, the chief office of the royal government while Richard I was abroad, and he kept it until 1198.",
        details = "The office carried the king's authority over the courts and the revenue. Walter was also archbishop of Canterbury from 1193, and he held the two posts together during the king's absence.",
    ),

)
