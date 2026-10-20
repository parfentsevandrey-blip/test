package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 321 to 328: Constantine's Sunday law, the battle of Chrysopolis, the Council of Nicaea and Athanasius's election at Alexandria. */
val db172Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "Constantine's Sunday law, which gave city people and craftsmen rest on the venerable day of the sun",
        originalTitle = "Venerabilis dies solis",
        author = "Constantine the Great",
        year = 321,
        summary = "In 321 Constantine ordered judges, city people and craftsmen to rest on the venerable day of the sun, an early imperial law that made Sunday a day of rest.",
        details = "Country people were allowed to keep working on the land, so the rest was meant for the towns, and the law names the day by its Latin planetary name, the day of the Sun. Accounts give 7 March 321 as its date, and the consuls named in that date are Crispus and Constantine, each in a second consulship.",
    ),

    entry(
        databaseId = 172,
        kind = Kind.SYSTEM,
        section = "Campaigns",
        title = "The battle of Chrysopolis, which left Constantine sole ruler of the Roman empire in 324",
        originalTitle = "Battle of Chrysopolis",
        author = "Constantine and Licinius",
        year = 324,
        summary = "On 18 September 324 Constantine defeated Licinius on the Asian shore opposite Byzantium, and so became the sole ruler of the Roman empire.",
        details = "The battle came after Constantine's victory at Adrianople on 3 July 324 and a naval defeat for Licinius at the Hellespont, and Licinius was later put to death at Thessalonica in 325. The date of 18 September is the one usually given for the battle.",
    ),

    entry(
        databaseId = 172,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Nicaea of 325, the first ecumenical council, which condemned Arius and issued a creed",
        originalTitle = "Synodos Nikaia",
        author = "Bishops summoned by Constantine",
        year = 325,
        summary = "The Council of Nicaea, called by Constantine in 325, condemned the teaching of Arius and issued a creed that became the basis of the Nicene faith.",
        details = "Tradition counts 318 bishops at the council, which also issued about twenty canons on church order, including a common date for Easter. Accounts differ on when it opened, some giving May and others June, while its close is usually given as August 325.",
    ),

    entry(
        databaseId = 172,
        kind = Kind.SYSTEM,
        section = "Church office",
        title = "Athanasius's election as bishop of Alexandria in 328, in place of Alexander",
        originalTitle = "Election of Athanasios",
        author = "The clergy and people of Alexandria",
        year = 328,
        summary = "Athanasius was chosen bishop of Alexandria in 328 after the death of Alexander, and his tenure brought long struggles with rivals in Egypt and at the imperial court.",
        details = "Accounts name him as Alexander's chosen successor, and his election was disputed by opponents among the Egyptian clergy. He was exiled several times during his episcopate and died in 373, so the disputes of his see lasted for decades.",
        rare = true,
    ),

)
