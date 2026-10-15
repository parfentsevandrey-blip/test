package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1750 to 1760: a naming scheme for plants and animals, a Russian grammar, a letter on signs and a shorthand. */
val db29Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.LANGUAGE,
        section = "Botany",
        title = "Linnaeus's binomial names in Species Plantarum",
        originalTitle = "Species Plantarum",
        author = "Carl Linnaeus",
        year = 1753,
        summary = "Each plant species gets a two-word name, a genus followed by an epithet, and the same pair is used in every reference.",
        details = "Linnaeus used the two-word names throughout the Species Plantarum of 1753, which is the starting point of plant nomenclature. The names replaced long descriptive phrases, and the same form was later applied to animals in the tenth edition of the Systema naturae.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Lomonosov's Russian grammar",
        originalTitle = "Rossiiskaia grammatika",
        author = "Mikhail Lomonosov",
        year = 1755,
        summary = "A grammar of Russian that sets out the noun and verb forms as one system, published at St Petersburg in 1755.",
        details = "Lomonosov wrote the grammar for the St Petersburg Academy of Sciences, and it appeared in 1755. Later grammars of Russian took its plan of noun and verb forms as a model.",
        rare = true,
    ),

    entry(
        databaseId = 29,
        kind = Kind.LANGUAGE,
        section = "Signs",
        title = "Diderot's letter on the deaf and dumb",
        originalTitle = "Lettre sur les sourds et muets",
        author = "Denis Diderot",
        year = 1751,
        summary = "Diderot argued that sign language has its own grammar and can express thought as fully as speech.",
        details = "The letter of 1751 describes deaf pupils who reason with signs and suggests that the order of signs differs from the order of spoken words. It made the case that signs are a language in their own right.",
        rare = true,
    ),

    entry(
        databaseId = 29,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Gurney's brachygraphy shorthand",
        originalTitle = "Brachygraphy",
        author = "Thomas Gurney",
        year = 1750,
        summary = "A shorthand of strokes and dots for words and word parts, first published in 1750, which stayed in use for many decades.",
        details = "The first edition of Gurney's system appeared in 1750, and sources give its full title in differing forms. The strokes and dots write sounds and short words, so the system was built for speed.",
        rare = true,
    ),

    entry(
        databaseId = 29,
        kind = Kind.LANGUAGE,
        section = "Electricity",
        title = "Franklin's positive and negative electricity",
        originalTitle = "Experiments and Observations on Electricity",
        author = "Benjamin Franklin",
        year = 1751,
        summary = "Franklin's words positive and negative for the two states of electric charge, printed in his book of 1751.",
        details = "Franklin's letters of 1747 onward used the words for the charge that a glass or a jar gains or loses, and the London book of 1751 put them into print. Accounts differ on when the terms were first used, but the book is the first printed form of the names.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.LANGUAGE,
        section = "Zoology",
        title = "Linnaeus's Systema naturae, tenth edition",
        originalTitle = "Systema naturae, tenth edition",
        author = "Carl Linnaeus",
        year = 1758,
        summary = "The tenth edition of Linnaeus's classification of nature, which gave animals the two-word names that plants had received.",
        details = "The edition of 1758 is the starting point of zoological names in the binomial form. Linnaeus arranged animals in classes, orders, genera and species in one scheme, and the book is the base of animal nomenclature.",
    ),

)
