package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1016 to 1018: the regency of Michinaga, the Russkaya Pravda, a Leonese town charter and Cnut's law codes. */
val db103Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "The regency of Fujiwara no Michinaga, which gave the Fujiwara control of the court in 1016",
        originalTitle = "Sessho",
        author = "Fujiwara no Michinaga",
        year = 1016,
        summary = "Fujiwara no Michinaga took the post of regent for the boy emperor Go-Ichijo in 1016, and the Fujiwara kept control of the court through marriage ties with the imperial house.",
        details = "Michinaga's daughter Shoshi was the mother of Go-Ichijo, which made the regent the boy emperor's grandfather on his mother's side. The regency is dated to 1016 in the usual accounts, and it marks the height of the Fujiwara system of rule by regents.",
    ),

    entry(
        databaseId = 103,
        kind = Kind.SYSTEM,
        section = "Rus'",
        title = "The Russkaya Pravda, the law code of the Rus', begun with the Pravda of Yaroslav",
        originalTitle = "Russkaya Pravda",
        author = "Yaroslav the Wise and later compilers",
        year = 1017,
        summary = "The Russkaya Pravda is the law code of the Rus', whose oldest part, the Pravda of Yaroslav, sets fines for killing, injury and theft among other things and is dated to about 1017 in some accounts.",
        details = "Accounts differ on the date, with some placing the Yaroslav part in the early eleventh century and others in about 1017. The oldest surviving copy of the longer text is a Novgorod manuscript of 1282, and the code was expanded later, including a version issued by Yaroslav's sons in 1072.",
    ),

    entry(
        databaseId = 103,
        kind = Kind.SYSTEM,
        section = "Leon",
        title = "The Fuero de Leon, a charter of town rights granted by Alfonso V in 1017",
        originalTitle = "Fuero de Leon",
        author = "Alfonso V of Leon",
        year = 1017,
        summary = "Alfonso V of Leon granted the Fuero de Leon in 1017, a charter that set out the rights, courts and fines of the townspeople of Leon.",
        details = "A fuero was a town charter that fixed the law a community followed, and many later charters in Leon and Castile followed the same pattern. The 1017 date is the usual one, and the charter was issued at a royal assembly in Leon.",
        rare = true,
    ),

    entry(
        databaseId = 103,
        kind = Kind.SYSTEM,
        section = "England",
        title = "Cnut's law code, pledged at Oxford in 1018 and issued as the Winchester laws about 1020",
        originalTitle = "Cnut's law code",
        author = "Cnut the Great",
        year = 1018,
        summary = "King Cnut of England is recorded as pledging at Oxford in 1018 to keep the laws of King Edgar, and the Winchester code of about 1020 is the best-known text that bears his name.",
        details = "The Anglo-Saxon Chronicle places the Oxford pledge in 1018, while the Winchester code is dated 1020 or 1021 by different accounts. Scholars divide the code into a church part and a secular part, and they differ on whether the 1018 gathering produced a code at all.",
    ),

)
