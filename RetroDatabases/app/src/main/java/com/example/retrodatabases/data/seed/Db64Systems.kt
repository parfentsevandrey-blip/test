package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1401 to 1409: an Ottoman interregnum, an English statute, Zheng He's voyages, a Prague voting decree and a council at Pisa. */
val db64Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.SYSTEM,
        section = "Empires",
        title = "The Ottoman interregnum after Ankara, 1402 to 1413",
        originalTitle = "Fetret devri",
        author = "The sons of Bayezid I",
        year = 1402,
        summary = "After Timur defeated Bayezid at Ankara in 1402, the Ottoman state broke into a war between Bayezid's sons that lasted until 1413.",
        details = "The sons fought for the throne in Anatolia and the Balkans, and Mehmed I emerged as sole ruler in 1413. Historians call the period the interregnum, and accounts differ on how far it weakened the Ottoman state.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The English statute that allowed heretics to be burned",
        originalTitle = "De heretico comburendo",
        author = "The English Parliament under Henry IV",
        year = 1401,
        summary = "The statute De heretico comburendo, passed by the English Parliament in 1401, set out a legal process for burning heretics who refused to recant.",
        details = "It was passed under Henry IV and became the legal basis for the burning of heretics in England during the following century. Historians differ on how often the statute was used in its first years, and the number of people burned under it is debated.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.SYSTEM,
        section = "Voyages",
        title = "Zheng He's treasure-fleet voyages from 1405",
        originalTitle = "Zheng He's treasure-fleet voyages",
        author = "Zheng He, for the Yongle emperor",
        year = 1405,
        summary = "Zheng He led the first Ming treasure-fleet voyage in 1405, and the voyages that followed took Chinese fleets across the seas of Southeast Asia, India and East Africa.",
        details = "The Yongle emperor ordered the voyages, and they ran in seven expeditions from 1405 to 1433. Accounts differ on the size of the first fleet and its crews, so the figures in the sources are not used here.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The Decree of Kutna Hora, a new vote count at Prague",
        originalTitle = "Decretum Kuttenbergense",
        author = "King Wenceslas IV of Bohemia",
        year = 1409,
        summary = "The Decree of Kutna Hora gave the Bohemian nation of Prague University three votes and each of the other nations one vote.",
        details = "The decree of January 1409 changed the voting at Charles University so that the Bohemian nation could outvote the foreign nations. Accounts differ on how the change was received, and it is named as a step in the Hussite dispute.",
        rare = true,
    ),

    entry(
        databaseId = 64,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Council of Pisa, which deposed two rival popes",
        originalTitle = "Concilium Pisanum",
        author = "The cardinals of the rival obediences",
        year = 1409,
        summary = "The Council of Pisa of 1409 deposed two rival popes and elected a third, which left the Western Schism with three claimants instead of two.",
        details = "The council named Alexander V in place of Gregory XII and Benedict XIII, who did not accept their removal. Its attempt to end the schism is usually counted a failure, and the Council of Constance later ended it.",
    ),

)
