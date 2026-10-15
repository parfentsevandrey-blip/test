package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1590 to 1600: Gresham College, a botanic garden, the English poor relief, Akbar's administration and a Dutch map patent. */
val db45Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.SYSTEM,
        section = "Learned societies",
        title = "Gresham College, lectures from 1597",
        originalTitle = "Gresham College",
        author = "Sir Thomas Gresham",
        year = 1597,
        summary = "Gresham College gave free public lectures in London from 1597 under the will of the merchant Sir Thomas Gresham, with chairs in seven subjects.",
        details = "Gresham died in 1579 and left money for the college, and accounts differ on whether its origin is dated to his death or to the first lectures of 1597. The seven chairs covered divinity, astronomy, geometry, music, law, physic and rhetoric, and the lectures were to be given in Gresham's house in Bishopsgate.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.SYSTEM,
        section = "Gardens",
        title = "The Leiden botanic garden of 1590",
        originalTitle = "Hortus Botanicus Leiden",
        author = "Leiden University",
        year = 1590,
        summary = "The botanic garden of Leiden University, laid out in 1590, grew plants for teaching medicine and became an early model for university gardens.",
        details = "The garden was founded by the university so that students could study the plants used in medicine. Clusius, who came to Leiden in 1593, brought plants from his own travels and gardens to it, and later university gardens in Europe took it as an example.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.SYSTEM,
        section = "Poor relief",
        title = "The English Poor Relief Act of 1598",
        originalTitle = "An Act for the Relief of the Poor",
        author = "English Parliament",
        year = 1598,
        summary = "The English poor relief Act of 1598 made each parish raise money by a local rate for its poor, with overseers to keep the accounts and find work for the poor.",
        details = "Accounts differ on the date, since the act of the session of 1597 to 1598 is dated 1597 in some accounts, when the year still began in March, and 1598 in others. The statute became a base for the Elizabethan poor law, and the Act of 1601 later consolidated the system.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "Akbar's administration in the Ain-i-Akbari",
        originalTitle = "Ain-i-Akbari",
        author = "Abul Fazl ibn Mubarak",
        year = 1598,
        summary = "The Ain-i-Akbari of 1598 recorded the offices, revenue and army ranks of the Mughal empire under Akbar, as a survey compiled by his minister Abul Fazl.",
        details = "The work forms the third part of Abul Fazl's chronicle of Akbar's reign, and it was completed in 1598, although accounts give other dates for its completion. It lists the mansab ranks, the provinces and the land revenue, so it is a record of administration rather than a law code.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.SYSTEM,
        section = "Patents",
        title = "A States-General patent for Plancius's world map",
        originalTitle = "Octrooi",
        author = "Petrus Plancius",
        year = 1594,
        summary = "On 12 September 1594 the States-General of the Dutch Republic granted Plancius a twelve-year patent for publishing and selling his world map.",
        details = "The patent gave Plancius the sole right to publish and distribute the map for twelve years, so that other printers were kept from copying it. Accounts of the Dutch maps of the period date the grant to 12 September 1594, and the map itself is not described here.",
        rare = true,
    ),

)
