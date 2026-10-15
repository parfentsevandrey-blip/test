package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1780 to 1790: chemical nomenclature, mechanics as algebra, sign language and catalogue names. */
val db26Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.LANGUAGE,
        section = "Chemistry",
        title = "Lavoisier's chemical nomenclature",
        originalTitle = "Methode de nomenclature chimique",
        author = "Guyton de Morveau, Lavoisier, Berthollet and Fourcroy",
        year = 1787,
        summary = "Chemical names built from the names of the elements, so that a name says what a compound contains.",
        details = "Four chemists published the method in Paris in 1787. It replaced the names inherited from alchemy with names such as oxygen, hydrogen and sulphuric acid, and it became the base of modern chemical language.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.LANGUAGE,
        section = "Chemistry",
        title = "Lavoisier's table of simple substances",
        originalTitle = "Traite elementaire de chimie",
        author = "Antoine Lavoisier",
        year = 1789,
        summary = "A list of substances that could not be broken down further, grouped as gases, non-metals, metals and earths.",
        details = "The list appeared in the 1789 textbook. Some entries, such as lime, were later shown to be compounds, but the grouping set the agenda for chemists for decades.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.LANGUAGE,
        section = "Mechanics",
        title = "Lagrange's mechanics without diagrams",
        originalTitle = "Mecanique analytique",
        author = "Joseph-Louis Lagrange",
        year = 1788,
        summary = "Mechanics written as algebra: the motion of a system follows from its energy and generalised coordinates.",
        details = "The book of 1788 gives the equations that carry Lagrange's name. In the preface he says that it contains no figures, and the notation of generalised coordinates is still in use.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.LANGUAGE,
        section = "Signs",
        title = "L'Epee's methodical signs for the deaf",
        originalTitle = "La veritable maniere d'instruire les sourds et muets",
        author = "Charles-Michel de l'Epee",
        year = 1784,
        summary = "A sign system that follows French word order, with one sign for each word and signs for the grammar.",
        details = "The Abbe de l'Epee taught deaf pupils in Paris and wrote his method down in 1784. His school became the model for later schools for the deaf, and his signs are an early written system of visual language.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.LANGUAGE,
        section = "Astronomy",
        title = "Messier's numbers for nebulae and star clusters",
        originalTitle = "Catalogue des nebuleuses et des amas d'etoiles",
        author = "Charles Messier",
        year = 1781,
        summary = "A numbered list of faint objects, which astronomers still use to name them.",
        details = "Messier made the list so that comet hunters would not mistake these objects for comets. The catalogue grew to about a hundred numbered objects, and the M numbers are still in use.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.LANGUAGE,
        section = "Astronomy",
        title = "Herschel's catalogue of double stars",
        originalTitle = "Catalogue of double stars",
        author = "William Herschel",
        year = 1782,
        summary = "A list of 269 close pairs of stars, with their positions, later used to find which pairs really orbit each other.",
        details = "Herschel published the list in 1782 and returned to the pairs for years. His later measurements showed that some of them are true binary stars, which gave the list a second use.",
        rare = true,
    ),
)
