package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1690 to 1700: two French-language dictionaries, Locke on words, a Russian grammar, Leibniz on China and Wallis's decoded letters. */
val db35Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "The French Academy's first dictionary of 1694",
        originalTitle = "Dictionnaire de l'Academie francoise",
        author = "Academie francaise",
        year = 1694,
        summary = "The French Academy printed its first dictionary in 1694, a list of French words with short definitions arranged alphabetically.",
        details = "Work on the book began in 1678, and the first edition is dated August 1694 after many years of revision. The Academy meant the book to fix the usage of polished French, and later editions became the standard reference for French words and their senses.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Furetiere's Dictionnaire universel",
        originalTitle = "Dictionnaire universel",
        author = "Antoine Furetiere",
        year = 1690,
        summary = "A French dictionary of words and things, printed in 1690 after its compiler's death, which competed with the Academy's own word book.",
        details = "Furetiere died in 1688, and the work appeared in 1690 as a rival to the Academy's project. The dispute cost him his seat in the Academy, and the rivalry is part of the story of the Academy's own dictionary of 1694.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.LANGUAGE,
        section = "Philosophy of words",
        title = "Locke's Essay on words and their meanings",
        originalTitle = "An Essay Concerning Human Understanding",
        author = "John Locke",
        year = 1690,
        summary = "Book III of Locke's Essay argues that words stand for ideas in the mind, and that many disputes come from words with loose meanings.",
        details = "The Essay was first published in London in 1690, and its third book is given over to words, general terms and the misuse of language. Locke's warning that words can hide confused thinking became a common point in later writing on language and logic.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Ludolf's grammar of the Russian language",
        originalTitle = "Grammatica Russica",
        author = "Hiob Ludolf",
        year = 1696,
        summary = "A grammar of Russian written in Latin for Western readers, one of the earliest such grammars of the language.",
        details = "Ludolf was a German scholar of Ethiopic who also studied Russian, and his grammar was printed in Latin in 1696. It set out the noun cases and verb forms for scholars who had no Russian teacher at hand.",
        rare = true,
    ),

    entry(
        databaseId = 35,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "Leibniz's Novissima Sinica on the Chinese script",
        originalTitle = "Novissima Sinica",
        author = "Gottfried Wilhelm Leibniz",
        year = 1697,
        summary = "A Latin book by Leibniz that urged Europe and China to exchange learning, with remarks on the Chinese script and its characters.",
        details = "Leibniz published the work in 1697 to encourage scientific and missionary exchange with China, and he praised the long learning of the Chinese. He also considered whether Chinese characters, which stand for ideas rather than sounds, could serve as a model for a universal script.",
        rare = true,
    ),


    entry(
        databaseId = 35,
        kind = Kind.LANGUAGE,
        section = "Ciphers",
        title = "Wallis's decoded letters in his Opera mathematica of 1699",
        originalTitle = "Opera mathematica, volume three",
        author = "John Wallis",
        year = 1699,
        summary = "Wallis printed the solutions of two letters he had decoded in the third volume of his collected mathematical works, published in 1699.",
        details = "Wallis served as chief cryptographer from 1643 to 1689, first for Parliament and then for the royal court, and he also worked informally as a codebreaker for William III. The two solved letters appeared in print only in his last years, in the third volume of the Opera mathematica.",
    ),

)
