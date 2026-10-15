package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1670 to 1680: a finite speed of light, a measured degree, two plant anatomies and a gap in Saturn's ring. */
val db37Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Roemer's finite speed of light",
        originalTitle = "Demonstration touchant le mouvement de la lumiere",
        author = "Ole Roemer",
        year = 1676,
        summary = "Roemer inferred from the timing of Jupiter's moon eclipses that light takes time to cross space, so its speed is finite.",
        details = "Roemer presented the result to the Paris academy in 1676, and it was printed in the Journal des scavans that year. He estimated about 22 minutes for light to cross the diameter of the Earth's orbit, and Huygens accepted the result, although the figure was later corrected.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.PRACTICE,
        section = "Geodesy",
        title = "Picard's measured degree of meridian",
        originalTitle = "La Mesure de la Terre",
        author = "Jean Picard",
        year = 1670,
        summary = "Picard measured a degree of meridian near Paris and found about 57,060 toises for one degree of the Earth's circumference.",
        details = "The fieldwork ran from 1669 to 1670, though accounts give 1668 for the start, and the account appeared in print in 1671 as La Mesure de la Terre. Newton used Picard's arc when he worked on the size and shape of the Earth.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.PRACTICE,
        section = "Botany",
        title = "Malpighi's Anatome plantarum",
        originalTitle = "Anatome plantarum",
        author = "Marcello Malpighi",
        year = 1675,
        summary = "Malpighi's plant anatomy describes the structure of seeds, stems and leaves with careful drawings made from microscope work.",
        details = "The first part was printed by the Royal Society in London in 1675, and a second part followed in 1679. Malpighi's drawings of plant tissues were among the earliest careful microscope studies of vegetable structure, and they were read beside Grew's work of the same years.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.PRACTICE,
        section = "Botany",
        title = "Grew's Anatomy of Vegetables begun",
        originalTitle = "The Anatomy of Vegetables begun",
        author = "Nehemiah Grew",
        year = 1672,
        summary = "Grew's first plant anatomy, printed in 1672, described the cells and vessels of plants with the help of the microscope.",
        details = "The Royal Society printed the work in London in 1672 under the title The Anatomy of Vegetables begun. Grew later enlarged the study into a full Anatomy of Plants, and his descriptions and drawings of plant structure became standard references.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Cassini's gap in Saturn's ring",
        originalTitle = "Gap in the ring of Saturn",
        author = "Giovanni Domenico Cassini",
        year = 1675,
        summary = "Cassini reported a dark gap in Saturn's ring in 1675, a division that was later named after him.",
        details = "Cassini announced the gap in 1675 from observations at Paris, and it was later named the Cassini division. The observation showed that the ring was not a single unbroken band.",
    ),

)
