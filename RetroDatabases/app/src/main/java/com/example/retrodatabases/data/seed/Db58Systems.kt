package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1460 to 1470: a university at Basel, the sack of Mainz, Ficino's circle at Careggi, Bessarion's Greek books and Pomponio's academy. */
val db58Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Basel, opened in 1460",
        originalTitle = "Universitas studii Basiliensis",
        author = "Pope Pius II, by the founding bull of 1459",
        year = 1460,
        summary = "Basel's university, founded by a papal bull of 1459, opened in 1460 with faculties of arts, theology, law and medicine.",
        details = "Pius II issued the founding bull in 1459, and the studium opened the following year with the usual four faculties. The Basel foundation is one of the early universities in the German lands, and 1460 is the opening year given in most histories.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.SYSTEM,
        section = "War and printing",
        title = "The sack of Mainz and the scattering of its printers",
        originalTitle = "Sack of Mainz, 28 October 1462",
        author = "Adolf of Nassau, against Archbishop Diether of Isenburg",
        year = 1462,
        summary = "The sack of Mainz in October 1462 drove its craftsmen out of the city, and some of the printers who had worked there moved to other towns.",
        details = "Adolf of Nassau took Mainz on 28 October 1462 in his contest with the archbishop Diether of Isenburg, and the city's craftsmen were dispersed. Accounts agree that printers from the Mainz workshops carried the craft to other towns, but the names and routes of those who left are disputed.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "Ficino's Platonic circle at Careggi",
        originalTitle = "Platonic Academy",
        author = "Marsilio Ficino and the Medici circle",
        year = 1462,
        summary = "Ficino's circle of readers of Plato met at Cosimo de' Medici's villa at Careggi from about 1462, and later writers named it the Platonic Academy.",
        details = "Cosimo gave Ficino the villa at Careggi, where the group read Plato and discussed him together. Whether the circle had formal rules or a founding act is disputed, so 1462 is a conventional start rather than an agreed foundation day.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "Bessarion's gift of Greek books to Venice",
        originalTitle = "Donatio Bessarionis",
        author = "Cardinal Basilios Bessarion",
        year = 1468,
        summary = "Cardinal Bessarion gave his collection of Greek books to the Venetian republic in 1468, and the gift became the core of the Marciana library.",
        details = "Bessarion, a Greek-born cardinal, offered his manuscripts of Greek authors to Venice, and the gift is the start of the Marciana library. Accounts differ on the date of formal acceptance by the Senate and on the number of volumes given.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "Pomponio Leto's Roman Academy, broken up in 1468",
        originalTitle = "Accademia Romana",
        author = "Pomponio Leto and his humanist circle",
        year = 1468,
        summary = "Pomponio Leto's circle of Roman humanists was broken up in 1468 when Pope Paul II had its members arrested.",
        details = "Paul II arrested the members on charges of paganism and conspiracy, and Leto was held for a time. Accounts differ on when the academy began, some dating it to the early 1460s, and historians disagree on how formal it ever was.",
        rare = true,
    ),

)
