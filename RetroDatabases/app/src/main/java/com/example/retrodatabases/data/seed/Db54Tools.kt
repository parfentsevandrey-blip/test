package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1500 to 1510: four world charts from Lisbon, Seville and Venice, a mathematics book with Leonardo's solids, and Copernicus's sketch of a sun-centred system. */
val db54Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Juan de la Cosa's world map of 1500",
        originalTitle = "Mapa mundi",
        author = "Juan de la Cosa",
        year = 1500,
        summary = "Juan de la Cosa's world map of 1500 is among the oldest surviving charts to show the Americas, drawn by a pilot who had sailed with Columbus.",
        details = "The map is painted on parchment and marks the Caribbean coasts and the lands found by the Spanish voyages across the Atlantic. Its maker had taken part in Columbus's early voyages, and the chart is kept at the Naval Museum in Madrid.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.TOOL,
        section = "Maps",
        title = "The Cantino planisphere of 1502",
        originalTitle = "Cantino planisphere",
        author = "Alberto Cantino",
        year = 1502,
        summary = "The Cantino planisphere of 1502 is a Portuguese world chart that was copied out of Lisbon for an Italian duke, and it shows the coast of Brazil and the Indian Ocean.",
        details = "The chart was made in Lisbon in 1502 and reached Ferrara, where Alberto Cantino bought a copy for Duke Ercole d'Este. It is one of the earliest charts to show the new Portuguese sea route round Africa and the lands it reached.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Waldseemuller's map that gave the name America",
        originalTitle = "Universalis Cosmographia",
        author = "Martin Waldseemuller",
        year = 1507,
        summary = "Waldseemuller's world map of 1507 is the first known to use the name America for the new lands, and it was printed on twelve woodblock sheets.",
        details = "The map and its small book were printed at Saint-Die in Lorraine in 1507, and the name is a Latin form of Amerigo Vespucci's first name. The only surviving copy of the large map is kept in Washington.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.TOOL,
        section = "Navigation charts",
        title = "The Padron Real, the master chart of Seville",
        originalTitle = "Padron Real",
        author = "Casa de Contratacion",
        year = 1508,
        summary = "The Padron Real was the official master chart of the Spanish discoveries, kept at Seville and corrected as new coasts were reported to the crown.",
        details = "The crown ordered the master chart to be kept at Seville in 1508, and Amerigo Vespucci held the post of pilot major in the same years. The original chart is lost, so historians rely on later copies and descriptions.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Pacioli's De divina proportione with Leonardo's figures",
        originalTitle = "De divina proportione",
        author = "Luca Pacioli",
        year = 1509,
        summary = "Pacioli's book of 1509 on the divine proportion was printed at Venice with drawings of solid figures by Leonardo da Vinci.",
        details = "The book praised the golden ratio for its uses in building and in the arts, and it set out rules of proportion for architects and painters. Leonardo's drawings of the solids were made for the text and printed with it at Venice in 1509.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Copernicus's Commentariolus, the sketch of his system",
        originalTitle = "Commentariolus",
        author = "Nicolaus Copernicus",
        year = 1510,
        summary = "Copernicus's short sketch of a sun-centred system circulated in manuscript among his friends, and it was printed only in the nineteenth century.",
        details = "He wrote the sketch about 1510, and it lists seven basic assumptions about the motions of the heavens. Its first public form was the handwritten copies, since the text was not printed until long after his death.",
        rare = true,
    ),

)
