package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1370 to 1377: Ibn Khaldun's method book, the Catalan Atlas, Corbechon's French encyclopedia, a Ming rhyme dictionary, Charles V's library inventory and the Yantraraja. */
val db67Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.TOOL,
        section = "History",
        title = "Ibn Khaldun's Muqaddimah, a book of method for history",
        originalTitle = "Muqaddimah",
        author = "Ibn Khaldun",
        year = 1377,
        summary = "Ibn Khaldun finished the Muqaddimah in 1377 as the introduction to his universal history, a book of method that explains how states and dynasties rise and fall.",
        details = "He wrote it in North Africa, and it sets out rules for judging reports of the past and the view that a dynasty lasts about three generations. Accounts differ on the exact place of writing, and the year 1377 is the usual date of completion.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.TOOL,
        section = "Maps",
        title = "The Catalan Atlas, a painted set of world maps made on Majorca",
        originalTitle = "Atles catala",
        author = "Abraham Cresques",
        year = 1375,
        summary = "The Catalan Atlas is a painted set of maps made on the island of Majorca in 1375, one of the best-known charts of the medieval world.",
        details = "The maps are commonly attributed to Abraham Cresques, a Jewish map maker of Majorca, and they show the known world with cities, rulers and trade routes. Accounts differ on how much of the work was his and on who commissioned it, so the attribution given here is the usual one.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.TOOL,
        section = "Encyclopedias",
        title = "Corbechon's French encyclopedia, a translation made for Charles V",
        originalTitle = "Livre des proprietes des choses",
        author = "Jean Corbechon",
        year = 1372,
        summary = "Jean Corbechon's French translation of Bartholomaeus Anglicus's encyclopedia was completed for King Charles V of France in 1372.",
        details = "The Latin original, On the Properties of Things, was written in the thirteenth century, and the French version made its subjects available at the royal court. The year 1372 is the one usually given for the translation.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.TOOL,
        section = "Rhymes",
        title = "Hongwu zhengyun, the Ming rhyme dictionary of 1375",
        originalTitle = "Hongwu zhengyun",
        author = "A commission under the Hongwu emperor",
        year = 1375,
        summary = "The Hongwu zhengyun is a Chinese rhyme dictionary of 1375 that set one standard reading for the sounds of characters at the Ming court.",
        details = "The emperor wanted a single authority for the sounds of characters in writing and in examinations. Accounts differ on the names of the scholars who did the work, so the book is credited to the commission.",
        rare = true,
    ),

    entry(
        databaseId = 67,
        kind = Kind.TOOL,
        section = "Library",
        title = "The Louvre inventory of Charles V's books, drawn up in 1373",
        originalTitle = "Inventaire de la librairie du Louvre",
        author = "Gilles Malet",
        year = 1373,
        summary = "Gilles Malet, the king's librarian, drew up an inventory of the royal library at the Louvre in 1373, one of the earliest royal library catalogues to survive.",
        details = "The inventory lists the books by subject and by shelf, and it shows how a royal library was ordered in the late fourteenth century. Accounts differ on the total number of volumes, since the count depends on how the books are grouped.",
        rare = true,
    ),

    entry(
        databaseId = 67,
        kind = Kind.TOOL,
        section = "Astrolabes",
        title = "Yantraraja, the Sanskrit treatise on the astrolabe of 1370",
        originalTitle = "Yantraraja",
        author = "Mahendra Suri",
        year = 1370,
        summary = "The Yantraraja is a Sanskrit treatise on the astrolabe by the Jain scholar Mahendra Suri, dated 1370, that explains how the instrument is made and used.",
        details = "The work was written at Firozabad near Delhi during the reign of Firoz Shah Tughluq, and it draws on Islamic astronomy as it reached India. The date 1370 is the one usually given, and accounts differ on how much of the treatise is original.",
        rare = true,
    ),

)
