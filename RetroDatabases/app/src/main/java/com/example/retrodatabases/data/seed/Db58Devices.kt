package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1460 to 1470: Alberti's cipher disk, the Dardanelles Gun and the printing houses of Subiaco, Cologne, Augsburg, Basel, Venice and Foligno. */
val db58Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Ciphers",
        title = "Alberti's cipher disk, the first polyalphabetic device",
        originalTitle = "De componendis cifris",
        author = "Leon Battista Alberti",
        year = 1467,
        summary = "Alberti's cipher disk uses two rings to change the alphabet during a message, and it is usually called the first Western polyalphabetic device.",
        details = "Alberti wrote his treatise on cipher writing about 1466 to 1467, at the request of a papal secretary, and it describes a fixed outer ring and a movable inner ring. Accounts give 1466 or 1467 for the writing, and the earliest printed form that can be named is a Venetian edition of his moral works in Italian, dated 1568.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "Ulrich Zell's press at Cologne",
        originalTitle = "Zell's press",
        author = "Ulrich Zell",
        year = 1466,
        summary = "Ulrich Zell set up printing at Cologne, and his first dated book is usually given as 1466.",
        details = "Zell is named as Cologne's first printer and is said to have died about 1507. Some accounts place his first dated book in 1466 and others suggest that his work began as early as 1463, while the flourishing of the Cologne press is dated to about 1465, so the start is disputed.",
        rare = true,
    ),

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "The Subiaco press of Sweynheym and Pannartz",
        originalTitle = "Subiaco press",
        author = "Conrad Sweynheym and Arnold Pannartz",
        year = 1465,
        summary = "Sweynheym and Pannartz set up a press at the monastery of Subiaco, and their 1465 Lactantius is usually named as the first book printed in Italy.",
        details = "The two German printers worked at the abbey of Subiaco before taking their trade on to Rome. Accounts differ on which Subiaco book came first, and the 1465 Lactantius is the one most often named.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "Zainer's printing house at Augsburg, from 1468",
        originalTitle = "Augsburg printing house of Gunther Zainer",
        author = "Gunther Zainer",
        year = 1468,
        summary = "Gunther Zainer set up printing at Augsburg, and his first dated book, a set of meditations on the life of Christ, was finished in March 1468.",
        details = "The colophon points to 12 March 1468 as the best-supported day, while other accounts give 14 March for his first dated book. Zainer is credited with about eighty books, and he is named as the first printer of Augsburg.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "Ruppel's press, the first at Basel",
        originalTitle = "Latin Bible of about 1468",
        author = "Berthold Ruppel",
        year = 1468,
        summary = "Berthold Ruppel is credited with Basel's first press, and a Latin Bible printed about 1468 is the earliest book known from it.",
        details = "Accounts give the start of his press anywhere from about 1463 to 1468, and modern catalogues date the Latin Bible to about 1468, though some argue for 1470 or later. Accounts also differ on whether Bernhard Richel printed the second part of that Bible.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Artillery",
        title = "The Dardanelles Gun, an Ottoman bronze bombard",
        originalTitle = "Dardanelles Gun",
        author = "Munir Ali, named as its designer",
        year = 1464,
        summary = "The Dardanelles Gun is a large Ottoman bronze bombard of about 1464, reportedly kept in pieces at Fort Nelson near Portsmouth.",
        details = "Accounts of the bombard give 1464 as its date and name Munir Ali as its designer, while another account gives 1465, so the approximate range is 1464 to 1465. One account adds that the gun is reportedly kept dismantled at Fort Nelson near Portsmouth.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "Johann of Speyer's press at Venice, from 1468",
        originalTitle = "Venetian press of Johann of Speyer",
        author = "Johann and Wendelin of Speyer",
        year = 1468,
        summary = "Johann of Speyer and his brother Wendelin set up a printing house at Venice in about 1468, and Johann's first book, Cicero's letters, is dated 1469.",
        details = "Accounts give the brothers' Venetian work as 1468 to 1477, and Johann's first book, Cicero's Epistolae ad familiares, is dated 1469, so the start is approximate at 1468 to 1469. Johann is thought to have died about 1469 or 1470, after which Wendelin ran the house until 1477. A History of Information entry for Venice reasons that the press was probably working before the grant of September 1469.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.DEVICE,
        section = "Printing presses",
        title = "Johann Neumeister's press at Foligno, from 1470",
        originalTitle = "Foligno press",
        author = "Johann Neumeister",
        year = 1470,
        summary = "Johann Neumeister set up a press at Foligno in Umbria in 1470, and its first known book is De bello Italico adversus Gothos, also of 1470.",
        details = "Cambridge University Library's Dante exhibition dates the launch of the press to 1470, and a 2019 Princeton University Library news item names De bello Italico adversus Gothos as its first printed book. A regional tourism page says the Orfini brothers backed the venture and housed the press in their family palace, while one account has Neumeister reaching Foligno as early as 1464. His supposed training under Gutenberg at Mainz is treated as an assumption rather than a documented fact.",
    ),

)
