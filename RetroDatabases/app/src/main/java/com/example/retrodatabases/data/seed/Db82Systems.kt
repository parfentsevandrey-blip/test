package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1222 to 1229: two universities, the Franciscan Rule, a Mongol kurultai and Hungary's Golden Bull. */
val db82Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Padua, begun by scholars who left Bologna in 1222",
        originalTitle = "Studium Patavinum",
        author = "Masters and students from Bologna",
        year = 1222,
        summary = "A group of masters and students left Bologna in 1222 and set up a studium generale at Padua, which became one of the oldest universities in Italy.",
        details = "Accounts give 1222 as the usual year of the move, and the first years of the school are poorly documented. Padua grew into a leading centre for law and medicine in the centuries that followed.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Naples, founded by Frederick II in 1224",
        originalTitle = "Studium generale Neapolitanum",
        author = "Frederick II, Holy Roman Emperor",
        year = 1224,
        summary = "Frederick II founded a studium generale at Naples by charter on 5 June 1224, a school meant to train the lawyers and officials of his southern kingdom.",
        details = "It was one of the first universities founded by a secular ruler, not by the Church or by a town, and it gave the emperor a school of his own. The date 5 June 1224 is the usual date of the charter, although teaching in the early years is poorly recorded.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.SYSTEM,
        section = "Orders",
        title = "The Franciscan Rule of 1223, approved by Pope Honorius III",
        originalTitle = "Regula bullata",
        author = "Francis of Assisi and Pope Honorius III",
        year = 1223,
        summary = "Pope Honorius III approved the Franciscan Rule in a bull of 29 November 1223, a short rule of twelve chapters that governed the Friars Minor.",
        details = "It revised Francis's earlier rule of 1221, which the Pope had not formally approved, and it sets out poverty, obedience and the daily life of the friars. Later quarrels over the strictness of poverty split the order, so the rule was read in more than one way.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.SYSTEM,
        section = "Mongols",
        title = "The kurultai of 1229, which chose Ogedei as Great Khan",
        originalTitle = "Kurultai",
        author = "The princes of the Mongol house",
        year = 1229,
        summary = "The Mongol princes met in a kurultai in 1229 and chose Ogedei, a son of Genghis Khan, as Great Khan after his father's death in 1227.",
        details = "A kurultai was a council of princes that settled the succession and major wars, and the empire had no khan in the two years after Genghis Khan died. Accounts agree on the year 1229, but the exact season and place of the assembly are less clearly recorded.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Golden Bull of Hungary, Andrew II's charter of noble rights in 1222",
        originalTitle = "Aranybulla",
        author = "King Andrew II of Hungary",
        year = 1222,
        summary = "King Andrew II of Hungary issued the Golden Bull in 1222, a charter that set out the rights of the nobles and limited the king's power over the nobles and their lands.",
        details = "It is often compared with Magna Carta, although it granted rights to the nobles rather than to all free men, and a clause allowed nobles to resist a king who broke its terms. Later kings confirmed the charter again, and the year 1222 is the usual date of its issue.",
    ),

)
