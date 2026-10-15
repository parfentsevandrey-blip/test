package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1680 to 1689: a royal hospital, a canal, a museum, a coffee house insurance market and a hearth tax. */
val db36Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.SYSTEM,
        section = "Care",
        title = "The Royal Hospital at Chelsea",
        originalTitle = "Royal Hospital, Chelsea",
        author = "Charles II, with Christopher Wren as architect",
        year = 1682,
        summary = "Charles II founded the Royal Hospital at Chelsea as a home for old and disabled soldiers, and Christopher Wren designed its buildings.",
        details = "A royal instrument dated December 1681 set up the foundation, the hospital was founded in 1682, and its first residents arrived only in 1692. The hospital still houses pensioners.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.SYSTEM,
        section = "Engineering",
        title = "The Canal du Midi opens from Toulouse to the sea",
        originalTitle = "Canal royal de Languedoc",
        author = "Pierre-Paul Riquet",
        year = 1681,
        summary = "A canal linking Toulouse with the Mediterranean opened in 1681, with flights of locks that lifted boats over the hills of Languedoc.",
        details = "Riquet, the engineer who planned the canal, died in 1680, the year before it opened. Its water came from reservoirs in the hills, and for much of its early life the canal was known as the Canal royal de Languedoc.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.SYSTEM,
        section = "Museums",
        title = "The Ashmolean Museum at Oxford",
        originalTitle = "The Ashmolean Museum at Oxford",
        author = "Elias Ashmole",
        year = 1683,
        summary = "Elias Ashmole's collections, given to Oxford, were shown in a building that opened in 1683 as one of the first public museums in Britain.",
        details = "Ashmole had given his collections to the University in 1677, and the museum's building was finished in 1683. Robert Hooke was appointed its first curator, and the displays covered natural curiosities and manuscripts.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.SYSTEM,
        section = "Insurance",
        title = "Lloyd's coffee house in Tower Street",
        originalTitle = "Lloyd's coffee house in Tower Street",
        author = "Edward Lloyd",
        year = 1688,
        summary = "Edward Lloyd's coffee house became a meeting place for shipping men and underwriters, and it grew into the marine insurance market that bears his name.",
        details = "Accounts differ on the opening: one gives 1686, while the earliest known mention is a London Gazette notice dated February 1688 in the old calendar, which is 1689 by modern reckoning. The house moved to Lombard Street in 1691, where it became known for ship news and marine insurance.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.SYSTEM,
        section = "Taxation",
        title = "The hearth tax, ended in 1689",
        originalTitle = "The hearth tax, ended in 1689",
        author = "Parliament of England",
        year = 1689,
        summary = "The hearth tax, which charged householders for each fireplace, ended in 1689 after twenty-seven years, and its returns counted the homes and hearths of England.",
        details = "Parliament set the tax up in 1662, and its collectors listed households by their hearths, which made the returns a source for later counts of houses and people. The tax was abolished in 1689, the year William and Mary took the throne, and parts of the returns survive.",
    ),

)
