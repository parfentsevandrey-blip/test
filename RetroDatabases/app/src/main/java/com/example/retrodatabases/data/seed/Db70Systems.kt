package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1347 to 1349: a Prague university, an English order of the Garter, an English wage ordinance, a Deccan sultanate and a Serbian law code. */
val db70Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "Charles University at Prague, founded in 1348",
        originalTitle = "Universitas Carolina",
        author = "Charles IV of Bohemia",
        year = 1348,
        summary = "Charles IV founded the university at Prague in 1348 on the model of Paris, and it became the first university in central Europe.",
        details = "The founding charter is dated April 1348, after a papal bull of 1347 had approved the plan, and the university taught arts, law, medicine and theology. Accounts differ on how many masters and students came in the first years.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.SYSTEM,
        section = "Orders",
        title = "The Order of the Garter, founded by Edward III in 1348",
        originalTitle = "Most Noble Order of the Garter",
        author = "Edward III of England",
        year = 1348,
        summary = "Edward III founded the Order of the Garter in 1348, a fellowship of knights that is still the senior order of chivalry in England.",
        details = "The founding is traditionally dated to St George's Day, 23 April 1348, and the first fellowship was the sovereign with twenty-five companions. The tale of a lost lady's garter that gave the order its name is a later story, and accounts differ on how the name arose.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.SYSTEM,
        section = "Labour",
        title = "The Ordinance of Labourers of 1349, a wage freeze after the plague",
        originalTitle = "Ordinance of Labourers",
        author = "Edward III and his council",
        year = 1349,
        summary = "The English Ordinance of Labourers of 1349 fixed wages at their pre-plague level and ordered workers to stay in their places after the Black Death.",
        details = "It was issued in June 1349 and was followed in 1351 by the Statute of Labourers, which repeated its rules. Historians differ on how far the ordinance was enforced and on whether wages rose anyway, so its effect on pay is debated.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.SYSTEM,
        section = "Empires",
        title = "The Bahmani Sultanate of the Deccan, founded in 1347",
        originalTitle = "Bahmani Sultanate",
        author = "Hasan Gangu, later Ala al-Din Bahman Shah",
        year = 1347,
        summary = "In 1347 the Muslim noble Hasan Gangu took the title Ala al-Din Bahman Shah and founded the Bahmani Sultanate, which broke away from Delhi in the Deccan.",
        details = "The capital moved from Gulbarga to Bidar in the fifteenth century, and the sultanate split into successor states in the early sixteenth century. A later legend names a Brahmin astrologer called Gangu as Hasan's master, and historians treat the story with caution.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "Dusan's Code, the Serbian law book of 1349",
        originalTitle = "Zakonik",
        author = "Stefan Dusan, king of Serbia",
        year = 1349,
        summary = "Stefan Dusan's law code, the Zakonik, was issued at an assembly in 1349 and was extended with new articles in 1354.",
        details = "The code sets out law on the church, the nobles and the courts for lands that Dusan had won from Byzantium. Accounts differ on how far the code was applied, and historians debate how much of the 1354 text was in force earlier.",
    ),

)
