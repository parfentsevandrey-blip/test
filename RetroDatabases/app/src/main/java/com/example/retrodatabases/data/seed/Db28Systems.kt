package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1760 to 1770: two canals, a boundary survey, a longitude trial scheme and an academy of the arts. */
val db28Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.SYSTEM,
        section = "Canals",
        title = "The Bridgewater Canal",
        originalTitle = "The Bridgewater Canal",
        author = "James Brindley, for the Duke of Bridgewater",
        year = 1761,
        summary = "A canal built to carry coal from the Duke of Bridgewater's mines at Worsley to Manchester, opened in 1761.",
        details = "The canal opened in 1761 and was engineered by James Brindley for the Duke of Bridgewater. Its aqueduct over the River Irwell at Barton was a striking work of the time. The canal is often cited as the start of canal building in Britain.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.SYSTEM,
        section = "Surveys",
        title = "The Mason and Dixon line survey",
        originalTitle = "The Mason and Dixon line survey",
        author = "Charles Mason and Jeremiah Dixon",
        year = 1763,
        summary = "A boundary between Pennsylvania and Maryland, surveyed with astronomical observations between 1763 and 1767.",
        details = "Mason and Dixon began the survey in 1763 and finished the line in 1767 after several years of astronomical observations. In the decades before the Civil War the line became a symbolic dividing line between the northern and southern states, and it is still known by their names.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.SYSTEM,
        section = "Canals",
        title = "Wedgwood's Etruria works",
        originalTitle = "Wedgwood's Etruria works",
        author = "Josiah Wedgwood",
        year = 1769,
        summary = "A pottery works in Staffordshire, opened in 1769, where the stages of making ware were divided among separate workshops.",
        details = "Wedgwood built the Etruria works in 1769 beside the Trent and Mersey canal. The works gave each stage of pottery making its own workshop, so that the work was divided among specialists.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.SYSTEM,
        section = "Learned institutions",
        title = "The Royal Academy of Arts",
        originalTitle = "The Royal Academy of Arts",
        author = "Joshua Reynolds and the founding members",
        year = 1768,
        summary = "An academy of painters, sculptors and architects founded in London with royal approval, which held a yearly public exhibition.",
        details = "The academy was founded in December 1768 with Joshua Reynolds as its first president. Its first public exhibition followed in 1769, and the exhibitions became an annual event.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.SYSTEM,
        section = "Navigation",
        title = "The Board of Longitude's sea trials of timekeepers",
        originalTitle = "The Board of Longitude's sea trials of timekeepers",
        author = "The Board of Longitude",
        year = 1761,
        summary = "A system under which the Board of Longitude tested marine timekeepers on voyages, so that a clock's error at sea could be turned into a longitude.",
        details = "The Board of Longitude sent timekeepers to sea so that their error could be measured against positions known by other means. In 1761 the H4 was carried on HMS Deptford to Jamaica, one of the trials of this kind.",
    ),
)
