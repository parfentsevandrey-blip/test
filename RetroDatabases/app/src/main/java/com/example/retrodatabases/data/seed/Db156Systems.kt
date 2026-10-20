package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 480 to 490: Zeno's decree of union on the faith, Clovis's victory at Soissons and the Three Elders of the Northern Wei. */
val db156Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "Zeno's Henotikon, the imperial decree of union on the faith, issued in 482",
        originalTitle = "Henotikon",
        author = "Emperor Zeno, drafted by Acacius of Constantinople",
        year = 482,
        summary = "The Henotikon was an imperial decree of union on the faith, issued by Zeno in 482 to end the split over the council of Chalcedon, and it was drafted by the patriarch Acacius.",
        details = "Accounts differ on how the decree treats Chalcedon: it honours the first three general councils by name, while one of its anathemas names Chalcedon itself. The quarrel with Rome that followed, the Acacian schism, ran from 484 to 519.",
    ),

    entry(
        databaseId = 156,
        kind = Kind.SYSTEM,
        section = "Gaul",
        title = "Clovis's defeat of the Roman general Syagrius at Soissons in 486",
        originalTitle = "Battle of Soissons",
        author = "Clovis, king of the Franks",
        year = 486,
        summary = "Clovis, king of the Franks, defeated Syagrius, the Roman commander who held the region around Soissons, and the battle brought that region under Frankish rule.",
        details = "Gregory of Tours reports that Syagrius fled to the Visigothic king Alaric II, who handed him over, and that Clovis had him put to death. The year 486 is the date usually given for the battle.",
    ),

    entry(
        databaseId = 156,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Three Elders of the Northern Wei, a system that set household headcounts beside the land grants",
        originalTitle = "Sanzhang zhi",
        author = "Empress Dowager Feng and Emperor Xiaowen",
        year = 486,
        summary = "The Three Elders were a Northern Wei arrangement that sought accurate household headcounts, so that land could be assigned under the equal-field decree.",
        details = "The reform is a companion of the equal-field decree of 485, and one account dates the Three Elders to 486. Accounts differ on the part played by Empress Dowager Feng, since the decree is credited to Emperor Xiaowen with Feng acting as regent, while one history gives her credit for both reforms.",
        rare = true,
    ),

)
