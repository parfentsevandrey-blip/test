package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1210 to 1220: the charter of 1215, the Paris statutes of the same year, the Lateran council, the Dominican approval of 1216 and the Salamanca studium. */
val db83Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "Magna Carta, the charter of liberties sealed at Runnymede",
        originalTitle = "Magna Carta Libertatum",
        author = "King John and his barons",
        year = 1215,
        summary = "King John sealed Magna Carta at Runnymede in June 1215, a charter that limited royal power and became a base for later English law.",
        details = "The first charter had sixty-three clauses on the king's rights, the courts and the liberties of free men, and it was reissued in 1216 and 1217. The version that stayed in the statute books was issued in 1225 with thirty-seven clauses.",
    ),

    entry(
        databaseId = 83,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "Robert de Courcon's statutes for the University of Paris",
        originalTitle = "Statuta",
        author = "Robert de Courcon",
        year = 1215,
        summary = "In August 1215 the papal legate Robert de Courcon issued statutes for the University of Paris that fixed the arts curriculum and named the books students had to read.",
        details = "The statutes allowed the logical works of Aristotle to be read under control. Later accounts say they carried forward the 1210 ban on lectures on his natural philosophy and metaphysics, while logic and ethics stayed open to teaching.",
    ),

    entry(
        databaseId = 83,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Fourth Lateran Council of 1215 and its seventy canons",
        originalTitle = "Concilium Lateranense IV",
        author = "Pope Innocent III and the council fathers",
        year = 1215,
        summary = "The Fourth Lateran Council met in Rome in November 1215 and issued seventy canons that set rules for the clergy, the sacraments and the crusades.",
        details = "Innocent III called the council, which opened on 11 November 1215, and its decrees defined transubstantiation among other rules. Its canons became a reference point for church law in the centuries that followed.",
    ),

    entry(
        databaseId = 83,
        kind = Kind.SYSTEM,
        section = "Religious orders",
        title = "The Order of Preachers, approved by Pope Honorius III in 1216",
        originalTitle = "Ordo Praedicatorum",
        author = "Dominic de Guzman and Pope Honorius III",
        year = 1216,
        summary = "Pope Honorius III confirmed the Order of Preachers, the Dominican friars, in a bull of December 1216 that gave the order its mission of preaching and study.",
        details = "Dominic had begun the group at Toulouse, and the approving bull is usually dated 22 December 1216. The order's first general chapter, held at Bologna in 1220, began to shape its constitutions.",
    ),

    entry(
        databaseId = 83,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "Salamanca's studium, traditionally dated to 1218",
        originalTitle = "Studium Salmantinum",
        author = "King Alfonso IX of Leon",
        year = 1218,
        summary = "The studium at Salamanca is traditionally dated to 1218, when Alfonso IX of Leon is said to have founded it, and its early history is thinly documented.",
        details = "The traditional year is 1218, but the earliest royal charter that survives for the school is usually dated to the 1250s, so accounts differ on when teaching began. The school grew into one of the great universities of Castile.",
    ),

)
