package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1880 to 1890: a logic game, slide rule, interferometer, dictionary part, ruling engine and logic machine. */
val db16Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.TOOL,
        section = "Logic games",
        title = "Carroll's Game of Logic",
        originalTitle = "The Game of Logic",
        author = "Lewis Carroll (Charles Lutwidge Dodgson)",
        year = 1886,
        summary = "A logic game with a diagram of cells for the four combinations of two classes, which taught readers to draw conclusions from premises.",
        details = "Carroll published The Game of Logic in 1886 as a teaching game for logic, built around a diagram. The cells of the diagram stand for the combinations of two classes, and the rules show how premises fix a conclusion. The game sits beside Carroll's other logic writings and was meant for general readers.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.TOOL,
        section = "Slide rules",
        title = "Thacher's cylindrical slide rule",
        originalTitle = "Thacher's calculating instrument",
        author = "Edwin Thacher",
        year = 1881,
        summary = "A cylindrical slide rule whose scales wind round a cylinder, so the scales are much longer than on a flat rule of the same size.",
        details = "Thacher's cylinder carried long log scales on a spiral, which gave an engineer more digits than a flat rule of the same size. Keuffel and Esser later made the instrument commercially. The record treats the cylinder as the tool and not the flat rules that came before it.",
        rare = true,
    ),

    entry(
        databaseId = 16,
        kind = Kind.TOOL,
        section = "Optical instruments",
        title = "Michelson's interferometer of 1881",
        originalTitle = "The Relative Motion of the Earth and the Luminiferous Ether",
        author = "Albert Abraham Michelson",
        year = 1881,
        summary = "A split-beam instrument that made interference fringes from two light paths, built to test whether the Earth moves through the ether.",
        details = "Michelson described the instrument in 1881, and its first result was too small to settle the question of the ether drift. He rebuilt it with Morley in 1887 for the more careful experiment. The interferometer then became a standard instrument for measuring wavelengths.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.TOOL,
        section = "Reference books",
        title = "The first part of the Oxford English Dictionary",
        originalTitle = "A New English Dictionary on Historical Principles",
        author = "James Murray and the Philological Society",
        year = 1884,
        summary = "The first part of a dictionary that traced each English word through dated quotations, issued in parts from 1884 over many years.",
        details = "The first part, covering the words from A to Ant, appeared in 1884 under the editorship of James Murray. The dictionary was built from dated quotations sent in by volunteers, and the whole was finished in 1928. The record is about the first part and not the complete dictionary.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.TOOL,
        section = "Spectroscopy",
        title = "Rowland's ruling engine for diffraction gratings",
        originalTitle = "Concave diffraction gratings",
        author = "Henry Augustus Rowland",
        year = 1882,
        summary = "A ruling engine that cut fine parallel lines for diffraction gratings, including concave gratings that focused a spectrum without a separate lens.",
        details = "Rowland built his ruling engine in the early 1880s and made gratings of unusual precision for spectroscopy. A concave grating focuses its spectrum without a separate lens, which simplified the instrument. Such gratings were soon used for measuring wavelengths in physics and astronomy.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.TOOL,
        section = "Logic machines",
        title = "Marquand's logic machine",
        originalTitle = "A New Logical Machine",
        author = "Allan Marquand",
        year = 1885,
        summary = "A logic machine with keys and linked parts that worked through the combinations of three classes, a successor to Jevons's logical piano.",
        details = "Marquand's machine dealt with the three terms of a syllogism and shut out the cases ruled out by each premise. Marquand had also written on logical diagrams for more than three terms, so his work links the machine to the diagrams of Venn. The machine is a mechanical counterpart to the shading method.",
        rare = true,
    ),
)
