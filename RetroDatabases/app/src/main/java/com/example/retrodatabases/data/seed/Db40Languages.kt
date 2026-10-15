package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1640 to 1650: a cipher book, the hand as a language, a Narragansett guide, French usage, a verse funnel and a Slavonic dictionary. */
val db40Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.LANGUAGE,
        section = "Secret writing",
        title = "Wilkins's Mercury, the secret and swift messenger",
        originalTitle = "Mercury: or the Secret and Svvift Messenger",
        author = "John Wilkins",
        year = 1641,
        summary = "A short English book on secret writing that gave a geometric cipher built from dots, lines and triangles set against an alphabet key.",
        details = "Sources often call it the first book in English on cryptology, and it gave English two new words for kinds of secret writing and of secrecy in speech. An Archivaria article by Eric Sams judges most of its schemes cleverer than practical.",
        rare = true,
    ),

    entry(
        databaseId = 40,
        kind = Kind.LANGUAGE,
        section = "Gesture",
        title = "Bulwer's Chirologia, the natural language of the hand",
        originalTitle = "Chirologia: or the Naturall Language of the Hand",
        author = "John Bulwer",
        year = 1644,
        summary = "Bulwer treated the hands as a natural language and set out a long list of hand signs with the meanings a speaker could give them.",
        details = "Chirologia was printed in London in 1644 with its companion Chironomia, which dealt with gesture as an art of speaking. Bulwer's Philocophus of 1648 turned to the deaf, for whom the hand was the only language they could use.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.LANGUAGE,
        section = "Native languages",
        title = "Roger Williams's Key into the language of America",
        originalTitle = "A Key into the Language of America",
        author = "Roger Williams",
        year = 1643,
        summary = "A short printed guide to the Narragansett language of New England, with words, phrases and notes for English settlers and traders.",
        details = "Williams wrote the book while he was in England seeking a charter for Rhode Island, and it was printed in London in 1643. It is one of the earliest printed guides to an Indigenous American language made by a European settler.",
        rare = true,
    ),

    entry(
        databaseId = 40,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Vaugelas's Remarks on the French language",
        originalTitle = "Remarques sur la langue francoise",
        author = "Claude Favre de Vaugelas",
        year = 1647,
        summary = "Vaugelas gathered his notes on correct usage into a book that told French speakers which words and constructions the best society used.",
        details = "The Remarks were printed in Paris in 1647 and became a standard reference for French grammar for the next century. Vaugelas judged usage by how the best speakers at court and in polite society talked, not by the rules of Latin.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.LANGUAGE,
        section = "German verse",
        title = "Harsdoerffer's Poetic Funnel for German verse",
        originalTitle = "Der Poetische Trichter",
        author = "Georg Philipp Harsdoerffer",
        year = 1647,
        summary = "A German manual that promised anyone could learn to write verse in a few hours by following rules for word choice, rhyme and metre.",
        details = "The first part was printed in Nuremberg in 1647, and later parts followed up to 1653, with a title that promised the art could be poured into the reader in six hours. The method works through lists of words and set patterns, so it is an early rule-based way of composing verse.",
        rare = true,
    ),

    entry(
        databaseId = 40,
        kind = Kind.LANGUAGE,
        section = "Slavonic dictionaries",
        title = "Mikalja's Croatian dictionary of 1649",
        originalTitle = "Blago jezika slovinskoga, talijanskoga i latinskoga",
        author = "Jakov Mikalja",
        year = 1649,
        summary = "A trilingual dictionary that gave Slavonic (Croatian) words with their Italian and Latin equivalents, printed at Loreto from 1649.",
        details = "The compiler, who worked in Italy under the name Giacomo Micaglia, had the printing started at Loreto in 1649 by the Serafini brothers, and the volume was finished at Ancona in 1651.Historians of Croatian count it among the first large dictionaries with Croatian as the headword language, and a 2012 article reports that Latin vocabulary dominates the book.",
        rare = true,
    ),

)
