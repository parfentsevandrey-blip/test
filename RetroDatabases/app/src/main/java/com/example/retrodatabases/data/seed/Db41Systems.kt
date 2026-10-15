package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1630 to 1640: a royal letter office, a language academy, a college, a printing decree and a tax on the counties. */
val db41Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.SYSTEM,
        section = "Post",
        title = "The English Letter Office of Charles I",
        originalTitle = "Royal Proclamation of Charles I for settling the Letter-office on Witherings",
        author = "Charles I and Thomas Witherings",
        year = 1635,
        summary = "In 1635 Charles I set up a Letter Office under Thomas Witherings, so that the royal post carried letters for the public between towns and to Scotland.",
        details = "The proclamation of 31 July 1635 set up the office for England and Scotland, and it directed a six-day round trip between London and Edinburgh. Accounts differ on its intent: the Royal Mail counts it as the start of its public letter service, while a later complaint from displaced postmasters says it ousted them unjustly.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.SYSTEM,
        section = "Language",
        title = "The Academie francaise founded by Richelieu",
        originalTitle = "Lettres patentes pour l'Academie francaise",
        author = "Cardinal Richelieu",
        year = 1635,
        summary = "Richelieu's Academie francaise was set up in 1635 to fix the rules and vocabulary of the French language, and it later compiled the dictionary that bears its name.",
        details = "Its letters patent from Louis XIII are dated January 1635, and its members met to prepare a dictionary and a grammar. The first dictionary appeared in 1694, many years after the founding.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.SYSTEM,
        section = "Education",
        title = "Harvard College founded at Cambridge in Massachusetts",
        originalTitle = "New College",
        author = "Massachusetts Bay Colony",
        year = 1636,
        summary = "The Massachusetts Bay Colony voted in 1636 to found a college at Newtowne, and the college took the name of the clergyman John Harvard in 1639 after his bequest.",
        details = "The General Court voted the money in October 1636, and Newtowne was renamed Cambridge in 1638. Harvard left the college his library and half his estate when he died in 1638, and its early ties to the first colonial printing press ran through the president, Henry Dunster.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "The Star Chamber decree on printing of 1637",
        originalTitle = "Decree of the Star Chamber concerning printers",
        author = "Court of Star Chamber",
        year = 1637,
        summary = "The Star Chamber decree of 1637 limited printing in England to a fixed number of master printers and presses, and it required a licence before a book was printed.",
        details = "The decree was issued by the Star Chamber court in the summer of 1637, and it named the printers allowed to keep presses in London and at the universities. Its limits on presses and its licensing rules were followed by the licensing acts that came after.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.SYSTEM,
        section = "Taxation",
        title = "Charles I's ship money writs, sent to coast and inland counties",
        originalTitle = "Writ of ship money",
        author = "Charles I",
        year = 1634,
        summary = "Charles I's ship money writs of 1634 required coastal towns to pay for ships, and the writs were extended to the inland counties in 1635.",
        details = "The scheme was extended to the whole country in 1635, and a court case of 1637 about John Hampden's refusal to pay gave the crown a legal victory. The Long Parliament abolished the tax in 1640, and the assessment lists that survive record each county's sums.",
    ),

)
