package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1660 to 1670: two trading companies, a royal society, a royal academy and an observatory. */
val db38Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.SYSTEM,
        section = "Companies",
        title = "The Company of Royal Adventurers into Africa",
        originalTitle = "The Company of Royal Adventurers of England Trading into Africa",
        author = "Charles II and the Duke of York",
        year = 1660,
        summary = "A company chartered in 1660 to trade on the West African coast, and the forerunner of the Royal African Company.",
        details = "Charles II granted the charter in 1660, and the Duke of York was among the company's leading backers. The company was reorganised in 1672 as the Royal African Company, and its later business centred on the trade in enslaved Africans.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Royal Society's royal charter",
        originalTitle = "Royal Society royal charter",
        author = "Charles II",
        year = 1662,
        summary = "A charter of 1662 that gave the Royal Society of London its corporate standing as a royal body for the study of natural knowledge.",
        details = "The charter is dated 15 July 1662, and the society had grown out of meetings of natural philosophers in London and Oxford. The charter gave it a legal existence and a royal patron, which set it apart from the private clubs of the period.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.SYSTEM,
        section = "Companies",
        title = "Colbert's French East India Company",
        originalTitle = "Compagnie des Indes orientales",
        author = "Jean-Baptiste Colbert",
        year = 1664,
        summary = "A French trading company set up under royal charter in 1664, with state money and monopoly rights to compete in the Indian Ocean trade.",
        details = "Colbert, the minister of Louis XIV, backed the company with royal funds and trading privileges, and the charter dates from 1664. Its early voyages were slow and costly, and the company was reorganised more than once in the decades that followed.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Academie royale des sciences in Paris",
        originalTitle = "Academie royale des sciences",
        author = "Jean-Baptiste Colbert, for Louis XIV",
        year = 1666,
        summary = "A royal academy of scientists set up in Paris in 1666, whose members were paid by the state and expected to work on its problems.",
        details = "The academy met in rooms of the royal library and gave its members salaries and a share of royal patronage. Christiaan Huygens, a Dutchman, moved to Paris in 1666 and became one of its leading members.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.SYSTEM,
        section = "Astronomy",
        title = "The Paris Observatory",
        originalTitle = "Observatoire de Paris",
        author = "Louis XIV, on Colbert's advice",
        year = 1667,
        summary = "A royal observatory built in Paris from 1667, designed by Claude Perrault, with Cassini as its first director from 1669.",
        details = "Work began in 1667 and the building was finished in 1672, while the Academie royale des sciences used it for observation. Cassini's direction made the observatory the centre of French observation for decades.",
    ),

)
