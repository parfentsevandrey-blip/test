package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1550 to 1560: Tartaglia's encyclopedia of numbers, Copernican tables, two English books by Recorde, a fish book and an animal encyclopedia. */
val db49Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Tartaglia's General trattato of numbers and measures",
        originalTitle = "General trattato di numeri et misure",
        author = "Niccolo Tartaglia",
        year = 1556,
        summary = "Tartaglia's General trattato of numbers and measures began to appear in 1556 as a large reference work on arithmetic, measures and the practical arts.",
        details = "Its six parts came out between 1556 and 1560, and the remainder appeared after Tartaglia's death in 1557. The second part of 1556 contains an array of binomial coefficients, which some call Tartaglia's triangle and others Pascal's triangle, and accounts differ on the priority of similar layouts.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Reinhold's Prutenic Tables of Copernican positions",
        originalTitle = "Tabulae Prutenicae",
        author = "Erasmus Reinhold",
        year = 1551,
        summary = "Reinhold's Prutenic Tables of 1551 gave planetary positions computed from Copernicus's models, and astronomers used them for decades.",
        details = "The tables were printed in 1551 and took their name from Prussia, where Reinhold's patron ruled. Reinhold computed them from Copernicus's planetary models, so the tables carried a heliocentric theory into the work of practical astronomers.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Recorde's Pathway to Knowledge, geometry in English",
        originalTitle = "The Pathway to Knowledge",
        author = "Robert Recorde",
        year = 1551,
        summary = "Recorde's Pathway to Knowledge of 1551 gave the first principles of geometry in English for readers who had no Latin.",
        details = "The book was printed in London in 1551 and set out the definitions and figures that open Euclid's geometry in English. It came before Recorde's algebra of 1557, and both books were written for practical readers rather than for scholars.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.TOOL,
        section = "Natural history",
        title = "Gesner's Historia animalium, the animal encyclopedia",
        originalTitle = "Historiae animalium",
        author = "Conrad Gesner",
        year = 1551,
        summary = "Gesner's Historia animalium began in 1551 as an illustrated survey of the animals known to European naturalists, gathered from ancient and recent authors.",
        details = "The first volume on quadrupeds was printed at Zurich in 1551, and later volumes on birds and on aquatic animals followed in the 1550s. Gesner named his sources for each animal, so that readers could check an entry against the authors who had described it.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.TOOL,
        section = "Natural history",
        title = "Dodoens's Cruydeboeck, a herbal in Dutch",
        originalTitle = "Cruydeboeck",
        author = "Rembert Dodoens",
        year = 1554,
        summary = "Dodoens's Cruydeboeck of 1554 described plants in Dutch, with woodcut pictures, for readers who had no Latin.",
        details = "The herbal was printed at Antwerp in 1554, and it was translated into French in 1557 and into English as A New Herball in 1578. Its woodcuts and its grouping of plants by likeness made it a reference for later botanists.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.TOOL,
        section = "Natural history",
        title = "Rondelet's sea fishes, Libri de piscibus marinis",
        originalTitle = "Libri de piscibus marinis",
        author = "Guillaume Rondelet",
        year = 1554,
        summary = "Rondelet's book on sea fishes, printed in parts in 1554 and 1555, described and pictured marine fish for physicians and naturalists.",
        details = "The work was printed at Lyon in two parts, which appeared in 1554 and 1555, and it drew on the fishes that Rondelet had studied on the Mediterranean coast. Its woodcut pictures of many fishes made it a reference for later naturalists.",
        rare = true,
    ),

)
