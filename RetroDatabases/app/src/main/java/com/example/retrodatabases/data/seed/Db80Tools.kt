package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1240 to 1250: Qin Jiushao's treatise, Carpini's report, Tusi's Almagest revision and Frederick II's book on hunting with birds. */
val db80Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Qin Jiushao's Mathematical Treatise in Nine Sections, 1247",
        originalTitle = "Shushu jiuzhang",
        author = "Qin Jiushao",
        year = 1247,
        summary = "Qin Jiushao completed his Mathematical Treatise in Nine Sections in 1247, a Chinese handbook of calculation that covers remainders, land measurement and the numerical solving of equations.",
        details = "The nine sections include a method for simultaneous remainders, called the great expansion, and a rule for the area of a triangle from its three sides. The date 1247 is the usual date of the finished book, and each problem is set out with its worked answer.",
    ),

    entry(
        databaseId = 80,
        kind = Kind.TOOL,
        section = "Travel",
        title = "John of Plano Carpini's report on the Mongols, written in Latin",
        originalTitle = "Historia Mongalorum",
        author = "John of Plano Carpini",
        year = 1247,
        summary = "John of Plano Carpini, sent out by Pope Innocent IV, wrote his Latin report on the Mongols in 1247 after a journey that took him to the court of Guyuk.",
        details = "He left Lyon in 1245 and reached the Mongol court in 1246, and his report describes the lands, customs and army of the Mongols. The date 1247 is the usual date of the finished text, and accounts differ on how much of it he saw himself.",
    ),

    entry(
        databaseId = 80,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Tusi's Tahrir al-Majisti, a revision of Ptolemy's Almagest",
        originalTitle = "Tahrir al-Majisti",
        author = "Nasir al-Din al-Tusi",
        year = 1247,
        summary = "Nasir al-Din al-Tusi's Tahrir al-Majisti of 1247 is a revision of Ptolemy's Almagest, the book in which he set out the Tusi couple.",
        details = "The book revises Ptolemy's Almagest and includes the Tusi couple among its mathematical devices. Accounts differ on the place where it was written and on the later editions of the text.",
        rare = true,
    ),

    entry(
        databaseId = 80,
        kind = Kind.TOOL,
        section = "Falconry",
        title = "Frederick II's book on hunting with birds, De arte venandi cum avibus",
        originalTitle = "De arte venandi cum avibus",
        author = "Frederick II, Holy Roman Emperor",
        year = 1240,
        summary = "Frederick II wrote a Latin treatise on hunting with falcons and other birds during the 1240s, a practical handbook on the birds, their training and their hunts.",
        details = "The book is divided into six parts, and its date is given as the 1240s, with accounts differing on the years of composition. It is one of the most detailed medieval handbooks of falconry.",
        rare = true,
    ),

)
