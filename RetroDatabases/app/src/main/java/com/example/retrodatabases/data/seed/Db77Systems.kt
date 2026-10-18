package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1274 to 1279: the papal conclave decree of 1274, Norway's national law, and three English statutes of Edward I, including the Statute of Mortmain. */
val db77Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.SYSTEM,
        section = "Papacy",
        title = "Ubi periculum, the 1274 rules for locking in cardinals at a papal election",
        originalTitle = "Ubi periculum",
        author = "Pope Gregory X",
        year = 1274,
        summary = "Ubi periculum, a decree issued in July 1274 at the Second Council of Lyon, set out how cardinals were to be shut in together to choose a new pope.",
        details = "It required a wait of ten days after a pope's death before the electors met, allowed each elector only one attendant, and housed the cardinals in one room without partitions, with outside messages barred. Food was cut back the longer the vote dragged on, and accounts differ on the day of issue, given as 7 or 16 July 1274, while its force was disputed after 1276, when Adrian V and John XXI moved to set it aside.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "Magnus Lagabote's national law of Norway, of 1274",
        originalTitle = "Landslov",
        author = "King Magnus Lagabote",
        year = 1274,
        summary = "King Magnus Lagabote gave Norway a national law code in 1274, the Landslov, which was meant to apply across the whole kingdom.",
        details = "The name means the law of the land, and the code was drafted for the whole kingdom rather than for one region. Accounts differ on how long the older regional laws it was meant to replace stayed in use, and the code shaped Norwegian law in later centuries.",
        rare = true,
    ),

    entry(
        databaseId = 77,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Statute of Westminster I of 1275, a reform of the common law",
        originalTitle = "Statutum de Westmonasterio primo",
        author = "Edward I and his Parliament",
        year = 1275,
        summary = "The first Statute of Westminster, passed in 1275 under Edward I, restated much of the common law and set out rules for the king's courts and for the rights of lords.",
        details = "It was one of several statutes that Edward I's government passed in the 1270s to correct the law and the courts. Much of its content restated older rules, and some of its provisions were new.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Statute of Gloucester of 1278, a royal statute on lords and courts",
        originalTitle = "Statutum de Gloucestria",
        author = "Edward I and his Parliament",
        year = 1278,
        summary = "The Statute of Gloucester, issued in 1278 under Edward I, set out the rights of lords and the procedure of their courts, and it followed the Westminster reforms.",
        details = "It belongs to the same drive against local abuses that led the king to order the quo warranto inquiries in the same year. Much of its content restated older custom, and it was followed by further statutes in the same decade.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.SYSTEM,
        section = "Land",
        title = "The Statute of Mortmain of 1279 on gifts of land to the Church",
        originalTitle = "Statutum de religiosis",
        author = "Edward I and his Parliament",
        year = 1279,
        summary = "The Statute of Mortmain of 1279 forbade the gift of land to religious houses without the king's licence, so that land stopped passing out of the service it owed to lords.",
        details = "Land given to churches and monasteries was said to be held in mortmain, a dead hand that owed no feudal services and paid no feudal dues. The statute closed that gap for the lords who lost their dues when land passed into religious hands for good.",
    ),

)
