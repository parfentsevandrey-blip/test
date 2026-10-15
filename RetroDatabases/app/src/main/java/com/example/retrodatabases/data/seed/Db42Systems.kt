package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1620 to 1630: a Roman congregation, a Paris address office, a Dutch company, a London newsbook and a Virginia count. */
val db42Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Congregation of Propaganda Fide",
        originalTitle = "Inscrutabili divinae providentiae arcano",
        author = "Pope Gregory XV",
        year = 1622,
        summary = "Pope Gregory XV set up the Roman congregation for the spread of the faith in 1622, a central office that later ran printing in many scripts.",
        details = "Gregory XV established the congregation by a papal bull of 1622, and it directed missions in many lands. Its printing house, which set type in many languages and scripts, dates from later in the 1620s, and accounts give its start date in different ways.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.SYSTEM,
        section = "Information",
        title = "Renaudot's Bureau d'adresse",
        originalTitle = "Bureau d'adresse et de rencontre",
        author = "Theophraste Renaudot",
        year = 1630,
        summary = "Renaudot opened a Paris office in 1630 where people could register requests for work, goods and services, and read them in a published register.",
        details = "The office opened in Paris in 1630 and kept registers of addresses and requests that clients could consult and post. It is an early central clearing office for information, and Renaudot's name is also linked with the Gazette, which began in 1631.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.SYSTEM,
        section = "Companies",
        title = "The Dutch West India Company",
        originalTitle = "Geoctroyeerde West-Indische Compagnie",
        author = "States General of the Dutch Republic",
        year = 1621,
        summary = "A Dutch chartered company for trade and colonies in the Americas and Africa, founded in 1621 by a charter from the States General.",
        details = "The States General granted the charter in 1621, and the company ran trade and colonies in the Atlantic. It is separate from the Dutch East India Company of 1602, and it was run by several chambers, one in each of the leading port cities.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.SYSTEM,
        section = "News",
        title = "Butter and Bourne's Weekly News of 1622",
        originalTitle = "Weekly News",
        author = "Nathaniel Butter and Nicholas Bourne",
        year = 1622,
        summary = "A London newsbook of 1622 printed in regular weekly numbers, one of the first English news sheets issued at a fixed weekly interval.",
        details = "Butter and Bourne issued the Weekly News in London in 1622 as a printed news sheet in regular weekly numbers. Earlier English news pamphlets came out irregularly, and accounts differ on which weekly number of 1622 came first, so the year is the date given here.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.SYSTEM,
        section = "Records",
        title = "The Virginia muster of 1624",
        originalTitle = "Muster of the inhabitants of Virginia",
        author = "Officers of the colony of Virginia",
        year = 1624,
        summary = "A count of the colonists in Virginia taken in 1624, after the Crown took the colony over from the Virginia Company.",
        details = "The muster listed the colonists by settlement and household in the year the Virginia Company lost its charter. The surviving returns are incomplete, and historians give different totals for the population of that year.",
        rare = true,
    ),

)
