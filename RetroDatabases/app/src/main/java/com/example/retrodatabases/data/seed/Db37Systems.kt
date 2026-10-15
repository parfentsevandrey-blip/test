package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1670 to 1680: an observatory, a penny post, a physic garden, an academy of architecture and a trading charter. */
val db37Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.SYSTEM,
        section = "Timekeeping",
        title = "The Royal Observatory at Greenwich",
        originalTitle = "The Royal Observatory at Greenwich",
        author = "Charles II",
        year = 1675,
        summary = "Charles II set up the Royal Observatory at Greenwich in 1675, with John Flamsteed as its first Astronomer Royal, to improve the observations that navigators needed.",
        details = "The observatory was founded by royal warrant in March 1675, and Flamsteed was appointed to observe the stars. The warrant gave the purpose as finding the longitude at sea, and that purpose shaped the observing work of the next half century.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.SYSTEM,
        section = "Post",
        title = "The London penny post of William Dockwra",
        originalTitle = "The London penny post",
        author = "William Dockwra",
        year = 1680,
        summary = "Dockwra's London penny post, begun in 1680, carried letters within the city for a penny each through receiving houses across the town.",
        details = "The service began in 1680 and was absorbed into the General Post Office in 1682. Its receiving houses let a letter be left near the sender's home, instead of only at the main offices.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.SYSTEM,
        section = "Gardens",
        title = "The Chelsea Physic Garden",
        originalTitle = "The Chelsea Physic Garden",
        author = "Society of Apothecaries",
        year = 1673,
        summary = "The Chelsea Physic Garden, founded in 1673 by the Society of Apothecaries, grew medicinal plants for study and teaching in London.",
        details = "The Society leased land at Chelsea so that apprentices and physicians could learn the plants used in medicine. Its botanical work in the following decades made the garden one of the early botanic gardens in England.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.SYSTEM,
        section = "Architecture",
        title = "The Academie royale d'architecture",
        originalTitle = "Academie royale d'architecture",
        author = "Jean-Baptiste Colbert",
        year = 1671,
        summary = "The Academie royale d'architecture was founded in Paris in 1671 under Colbert's patronage, to teach building by reasoned rules.",
        details = "Its members gave lectures on the rules of building, and the academy was one of the bodies Colbert used to bring order to royal construction in Paris. Its teaching was tied to the other academies Colbert set up in the same years.",
        rare = true,
    ),

    entry(
        databaseId = 37,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "The Hudson's Bay Company charter",
        originalTitle = "The Hudson's Bay Company charter",
        author = "Charles II",
        year = 1670,
        summary = "Charles II granted the Hudson's Bay Company a charter in 1670, giving it a trade monopoly over the lands that drain into Hudson Bay.",
        details = "The charter of 2 May 1670 made the company's governor and committee the proprietors of those lands. The company kept its trade monopoly there until the lands passed to Canada in 1870.",
    ),

)
