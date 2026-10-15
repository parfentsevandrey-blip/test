package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1670 to 1680: a universal characteristic, three dictionaries, a treatise on speech and a glossary of rarely used words. */
val db37Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "Leibniz's universal characteristic",
        originalTitle = "Characteristica universalis",
        author = "Gottfried Wilhelm Leibniz",
        year = 1679,
        summary = "Leibniz planned a universal characteristic, a notation in which each concept would have a sign and reasoning would become a kind of calculation.",
        details = "Leibniz wrote about this plan in papers of the late 1670s, and the idea returns in his later letters and drafts. Most of these drafts stayed unpublished until Couturat edited them in 1903, so their effect on later logic came slowly. This record uses 1679 as the working date of the drafts.",
        rare = true,
    ),

    entry(
        databaseId = 37,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Richelet's monolingual French dictionary",
        originalTitle = "Dictionnaire francois",
        author = "Pierre Richelet",
        year = 1680,
        summary = "Richelet's dictionary explained French words in French, in one alphabetical order, for readers who needed no Latin gloss.",
        details = "Richelet published the dictionary at Geneva in 1680, and it was one of the first large dictionaries of French that defined each word in French. The Academie francaise published its own dictionary only in 1694, so Richelet's book came out some years before it.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Coles's English dictionary",
        originalTitle = "An English Dictionary",
        author = "Elisha Coles",
        year = 1676,
        summary = "Coles's English dictionary gave short meanings for English words, an early step towards the general word books of the next century.",
        details = "Coles published his dictionary in London in 1676, with brief definitions of hard English words. It came before the larger English dictionaries of the eighteenth century, and those later works built on the same aim of explaining words to readers.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.LANGUAGE,
        section = "Rhetoric",
        title = "Lamy's art of speaking",
        originalTitle = "L'Art de parler",
        author = "Bernard Lamy",
        year = 1675,
        summary = "Lamy's book on speaking treated grammar and rhetoric as one art, teaching how clear thought should be carried into clear words.",
        details = "Lamy published L'Art de parler in Paris in 1675, reasoning about language in the Cartesian manner of ordered ideas. His main claim is that words should follow the order of thought, which tied grammar to logic.",
    ),

    entry(
        databaseId = 37,
        kind = Kind.LANGUAGE,
        section = "Oriental languages",
        title = "Meninski's dictionary of Turkish, Arabic and Persian",
        originalTitle = "Thesaurus linguarum orientalium",
        author = "Franciscus a Mesgnien Meninski",
        year = 1680,
        summary = "Meninski's Thesaurus gave Turkish, Arabic and Persian words in one dictionary, a large aid for European readers of those languages.",
        details = "The Thesaurus was printed at Vienna in 1680, and it gathered the three languages into one work. Its Turkish entries were of practical use to the diplomats and interpreters who dealt with the Ottoman Empire.",
        rare = true,
    ),

    entry(
        databaseId = 37,
        kind = Kind.LANGUAGE,
        section = "Glossaries",
        title = "Ray's list of rarely used words",
        originalTitle = "A Collection of English Words not generally used",
        author = "John Ray",
        year = 1674,
        summary = "Ray's list gathered English words that were no longer in general use, with their meanings, for readers of older and country speech.",
        details = "Ray published the collection in London in 1674, and it drew on speech from several counties and on older English writing. The book is a glossary of words that were falling out of everyday use in his time.",
        rare = true,
    ),

)
