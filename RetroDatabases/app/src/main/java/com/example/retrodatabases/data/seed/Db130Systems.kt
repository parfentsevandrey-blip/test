package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 740 to 750: the Ecloga of 741, the Konden land law and the Great Buddha decree of 743, and Boniface's council of 742. */
val db130Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Ecloga, a Byzantine law book of Leo III and his son Constantine V, dated 741",
        originalTitle = "Ekloge ton nomon",
        author = "Leo III and Constantine V",
        year = 741,
        summary = "The Ecloga, a Byzantine law book issued in the names of Leo III and his son Constantine V, is dated 741 here, though some scholars put it in 726.",
        details = "It selects and adjusts Justinian's laws, adds church law and older practice, and is arranged in eighteen titles written for judges and ordinary readers. Its rules on marriage and on sex-related crimes are among its main concerns, and accounts differ on whether it was issued in 726 or in 741.",
    ),

    entry(
        databaseId = 130,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "Emperor Shomu's decree of 743 to make a giant Vairocana Buddha, later set up at Nara",
        originalTitle = "Daibutsu zoryu no mikotonori",
        author = "Emperor Shomu",
        year = 743,
        summary = "Emperor Shomu's decree of 743 set out the plan for a giant bronze Vairocana Buddha, which was later set up at Nara.",
        details = "The decree was issued while the court was at Shigaraki, and the statue was dedicated at Nara in 752. Accounts differ on when the casting began, and published dates for the work run from 743 to 757, so the year used here is the year of the decree.",
    ),

    entry(
        databaseId = 130,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Konden Einen Shizaiho of 743, a land law that let newly reclaimed fields stay in private hands for ever",
        originalTitle = "Konden Einen Shizaiho",
        author = "The Japanese court",
        year = 743,
        summary = "The Konden Einen Shizaiho of 743 let newly reclaimed fields be held as private property for ever, within limits set by rank.",
        details = "It allowed people and temples who opened new paddy land to keep it, and historians often read it as a turn away from the state's allotment of fields to households. The law is dated 743, and scholars still debate how far its limits were applied.",
        rare = true,
    ),

    entry(
        databaseId = 130,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Concilium Germanicum of 742, the first Frankish reform council that Boniface led under Carloman",
        originalTitle = "Concilium Germanicum",
        author = "Boniface and Carloman",
        year = 742,
        summary = "The Concilium Germanicum of 742 was a reform council of the Frankish church held under Carloman, with Boniface at its head.",
        details = "It met in the eastern Frankish lands under Carloman's authority and aimed to restore church order and bring bishops under canon law. It is the first of the reform councils Boniface directed in the Frankish realm, and the year given for it is 742.",
        rare = true,
    ),

)
