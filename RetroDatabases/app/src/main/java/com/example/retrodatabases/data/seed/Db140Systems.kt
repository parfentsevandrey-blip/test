package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 640 to 650: Rothari's Lombard law code of 643, Umar's council of 644, the Taika reform of 646, the Typos of 648 and the Lateran synod of 649. */
val db140Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.SYSTEM,
        section = "Lombard",
        title = "The Edict of Rothari, the first written law code of the Lombards, issued at Pavia in 643",
        originalTitle = "Edictus Rothari",
        author = "Rothari, king of the Lombards",
        year = 643,
        summary = "The Edict of Rothari, the first written code of Lombard law, was issued at Pavia in 643 by King Rothari.",
        details = "It was issued in Latin and set out Lombard custom in written chapters, among them rules on compensation for injuries and on legal procedure. It is dated to 643, and later Lombard kings went on issuing laws of their own.",
        rare = true,
    ),

    entry(
        databaseId = 140,
        kind = Kind.SYSTEM,
        section = "Arabic",
        title = "Umar's council of six electors, named in 644 to choose a caliph from among its own members",
        originalTitle = "al-shura",
        author = "Umar ibn al-Khattab",
        year = 644,
        summary = "Umar, dying in 644, named a council of six electors to choose the next caliph from among its own members.",
        details = "The council chose Uthman, who became the third caliph later in 644. Umar's instruction for a split vote, as it is reported, gave the casting side to Abd al-Rahman ibn Awf, and accounts differ on how the council worked in practice.",
    ),

    entry(
        databaseId = 140,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Taika reform edict of 646 in the Nihon Shoki, with its rules on public land, household registers and taxes",
        originalTitle = "Taika no kaishin",
        author = "The court of Emperor Kotoku",
        year = 646,
        summary = "The Taika reform edict, dated 646 in the Nihon Shoki, set out rules on public land, household registers and taxes after the coup of 645.",
        details = "It aimed to replace private holdings of land and people with allotments under the state, and it followed the Isshi incident of 645. Scholars dispute how much of the text in the chronicle is original, and accounts differ on how far its rules were carried out at once.",
    ),

    entry(
        databaseId = 140,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Typos of Constans II, an imperial decree forbidding debate over Christ's wills, dated 648",
        originalTitle = "Typos",
        author = "Constans II",
        year = 648,
        summary = "The Typos of Constans II, an imperial decree that forbade debate over whether Christ had one or two wills, is dated to 648.",
        details = "The decree tried to end the monothelite dispute by silence, and the Lateran synod of 649 condemned it. Accounts date it to September 647 or to 648, and 648 is the year used here.",
        rare = true,
    ),

    entry(
        databaseId = 140,
        kind = Kind.SYSTEM,
        section = "Papal",
        title = "The Lateran synod of 649 under Pope Martin I, which condemned Monothelitism and the Typos",
        originalTitle = "Concilium Lateranense",
        author = "Pope Martin I",
        year = 649,
        summary = "The Lateran synod of October 649, called by Pope Martin I at Rome, condemned Monothelitism and the Typos of Constans II.",
        details = "The synod denounced the imperial decree and the monothelite teaching behind it, and the Byzantine government later moved against the pope. Accounts differ on the year of Martin's arrest, 653 or 654.",
        rare = true,
    ),

)
