package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1520 to 1530: a council for the Indies, an imperial edict on printed books, a regency, a Zurich Bible school and a Protestant university. */
val db52Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.SYSTEM,
        section = "Government",
        title = "The Council of the Indies, set up in 1524",
        originalTitle = "Consejo Real y Supremo de las Indias",
        author = "Charles V",
        year = 1524,
        summary = "The Council of the Indies, set up by Charles V in 1524, governed the Spanish lands overseas from Spain by sending laws and orders across the Atlantic.",
        details = "The council was created in 1524 and it controlled the courts, the appointments and the trade of the Spanish lands in America, with its records kept in Spain. It lasted until 1834, and the archive of its papers, now in Seville, holds the ledgers of the Atlantic trade.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "The Edict of Worms against Luther's books",
        originalTitle = "Edictum Wormatiense",
        author = "Charles V",
        year = 1521,
        summary = "The Edict of Worms, issued by Charles V in 1521, outlawed Luther and ordered his writings to be burned.",
        details = "The edict was dated at Worms on 8 May 1521, after Luther had refused to retract his teaching before the Diet, and it forbade the printing, sale and reading of his books across the empire. Its ban was not enforced in every territory, and printing of his works went on in Wittenberg and elsewhere.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.SYSTEM,
        section = "Government",
        title = "The Royal Audiencia of New Spain, set up in 1527",
        originalTitle = "Real Audiencia de Nueva Espana",
        author = "Charles V",
        year = 1527,
        summary = "The Royal Audiencia of New Spain, set up in 1527 by Charles V, was the high court of the new colony and sat at Mexico City.",
        details = "Its judges heard the cases of the colony and advised on its government, as the seat of royal justice in the Spanish lands of Mexico. Accounts differ on the exact day of its first sitting, so only the year of its setting-up is given here.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "The Zurich Prophezei, a school of Bible languages",
        originalTitle = "Prophezei",
        author = "Ulrich Zwingli",
        year = 1525,
        summary = "The Prophezei was a daily school of Bible study set up at Zurich in 1525, where scholars read the Hebrew, Greek and Latin texts of the Bible aloud and explained them.",
        details = "Zwingli founded the lectures so that preachers could read the Scriptures in their original languages, and the translation worked out by its scholars became the Zurich Bible of 1531. Accounts differ on how many lectures were given each day and on the exact founding date, so only the year is given here.",
        rare = true,
    ),

    entry(
        databaseId = 52,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "The University of Marburg, founded in 1527",
        originalTitle = "Universitas Marpurgensis",
        author = "Philip of Hesse",
        year = 1527,
        summary = "The University of Marburg, founded in 1527 by Landgrave Philip of Hesse, is often named the first university founded under the Reformation.",
        details = "Philip issued the charter in 1527 for a university with faculties of theology, law, medicine and arts, and it was run on Lutheran lines from its start. Accounts differ on whether it deserves the name of the first Protestant university, since the claim depends on how a Protestant university is defined.",
    ),

)
