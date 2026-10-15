package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1590 to 1600: Mercator's atlas, Kepler's solids, Wright's navigation, Hakluyt's voyages, Waghenaer's sea atlas and Gerard's herbal. */
val db45Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Mercator's Atlas, the book of maps",
        originalTitle = "Atlas sive cosmographicae meditationes de fabrica mundi",
        author = "Gerard Mercator",
        year = 1595,
        summary = "Mercator's Atlas of 1595 gathered a set of maps into one bound book, and its title made atlas the name for such a book.",
        details = "Gerard Mercator died in December 1594, and his son Rumold published the book in 1595 from the maps his father had drawn. The title word atlas, after the Titan who holds up the heavens, was used here for a whole book of maps.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Kepler's Mysterium cosmographicum, the five solids",
        originalTitle = "Mysterium cosmographicum",
        author = "Johannes Kepler",
        year = 1596,
        summary = "Kepler's Mysterium cosmographicum of 1596 fitted the six planetary spheres between five regular solids, to explain the number and spacing of the planets.",
        details = "The book was printed at Tubingen in 1596 and gave a model of the planetary orbits built from nested solids, with a sphere between each pair. Kepler later found the model wrong in its detail, but the book was his first major work and set out ideas that led to his later laws.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Wright's Certaine Errors in Navigation",
        originalTitle = "Certaine Errors in Navigation",
        author = "Edward Wright",
        year = 1599,
        summary = "Wright's Certaine Errors in Navigation of 1599 explained the mathematics of the Mercator chart, where every distance is stretched by the secant of the latitude.",
        details = "The book was printed in London in 1599, and it gave the reasons for the chart's stretched scale, which Mercator had drawn without a full account of the method. Later navigators used the book's method, and it is a key text in the history of the projection.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.TOOL,
        section = "Voyages",
        title = "Hakluyt's Principal Navigations, second edition",
        originalTitle = "The Principall Navigations, Voiages and Discoveries of the English Nation",
        author = "Richard Hakluyt",
        year = 1598,
        summary = "Hakluyt's Principal Navigations, in its second edition of 1598 to 1600, gathered the accounts of English voyages and travels into three large volumes.",
        details = "The second edition appeared in three volumes between 1598 and 1600, and the first edition had come out in 1589 as a single book. The collection gathered voyage narratives, letters and sailing accounts from English sailors and merchants, and it became a standard source for English voyages.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.TOOL,
        section = "Sea atlases",
        title = "Waghenaer's Thresoor der Zeevaerdt",
        originalTitle = "Thresoor der Zeevaerdt",
        author = "Lucas Janszoon Waghenaer",
        year = 1592,
        summary = "Waghenaer's Thresoor of 1592 was a sea atlas of coastal charts with sailing directions, the sequel to his Spieghel der Zeevaerdt of 1584.",
        details = "The book was printed in 1592 and carried coastal charts, soundings and directions that pilots used along the Dutch and neighbouring coasts. It follows the Spieghel of 1584, and it is a dated source for the Dutch pilot books of the late sixteenth century.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.TOOL,
        section = "Herbals",
        title = "Gerard's Herball of 1597",
        originalTitle = "The Herball, or Generall Historie of Plantes",
        author = "John Gerard",
        year = 1597,
        summary = "Gerard's Herball of 1597 described English and foreign plants with woodcuts and notes on their uses, and it became the best-known English herbal of its age.",
        details = "The book was printed in London in 1597 by John Norton and drew on the author's garden and on earlier continental herbals. Thomas Johnson revised it in 1633, and the revised edition remained in use for many years.",
    ),

)
