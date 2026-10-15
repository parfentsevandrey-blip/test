package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1660 to 1670: a shorthand diary, a general grammar, an Ethiopic grammar, a German grammar, a Bible in an American language and a real character. */
val db38Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Pepys's diary in shorthand",
        originalTitle = "The Diary of Samuel Pepys",
        author = "Samuel Pepys",
        year = 1660,
        summary = "Samuel Pepys kept his diary from 1660 to 1669 in a shorthand based on Thomas Shelton's system, so the entries could not be read without a key.",
        details = "Pepys wrote the diary in English but in signs, and most readers could not open it until it was deciphered and printed in the 19th century. The shorthand kept his private entries from casual eyes, and the diary is now one of the richest English records of the Restoration.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "The Port-Royal General Grammar",
        originalTitle = "Grammaire generale et raisonnee",
        author = "Antoine Arnauld and Claude Lancelot",
        year = 1660,
        summary = "A grammar that explains the parts of speech and the forms of sentences by reasons that its authors held to be common to all languages.",
        details = "Arnauld and Lancelot, both scholars of Port-Royal, printed the grammar in Paris in 1660, and it was reprinted and read for generations. Later writers argued over whether it described a reason shared by all languages or simply the usage of French and Latin, and that argument still shapes how it is judged.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.LANGUAGE,
        section = "Ethiopic",
        title = "Ludolf's Ethiopic grammar and lexicon",
        originalTitle = "Grammatica aethiopica",
        author = "Job Ludolf",
        year = 1661,
        summary = "A Latin grammar and dictionary of Ge'ez, the old literary language of Ethiopia, printed at Frankfurt for European scholars.",
        details = "Ludolf printed the grammar and a Latin lexicon of Ge'ez in 1661, which made the language available in print to European scholars. Later scholars corrected many of his readings, but the works remained the base for Ethiopic study in Europe for a long time.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Schottelius's German grammar of 1663",
        originalTitle = "Ausfuehrliche Arbeit von der Teutschen HaubtSprache",
        author = "Justus Georg Schottelius",
        year = 1663,
        summary = "A long German grammar that argued the language could make its own words from roots, compounds and derivations without foreign help.",
        details = "Schottelius printed the work at Braunschweig in 1663 as a large survey of German roots, compounds and inflections. He claimed that German was a language of its own with a self-sufficient stock of words, and later grammarians judged some of his claims to be overambitious.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.LANGUAGE,
        section = "Native languages",
        title = "Eliot's Bible in the Massachusett language",
        originalTitle = "Mamusse Wunneetupanatamwe Up-Biblum God",
        author = "John Eliot",
        year = 1663,
        summary = "Eliot's translation printed the whole Bible in the Massachusett language at Cambridge in Massachusetts, the first Bible printed in British North America.",
        details = "Eliot made the translation with the help of Native speakers and printed it in 1663, using an English-letter spelling of his own. Accounts differ on how many copies were printed and how many survive.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.LANGUAGE,
        section = "Universal language",
        title = "Wilkins's essay toward a real character and a philosophical language",
        originalTitle = "An Essay towards a Real Character, and a Philosophical Language",
        author = "John Wilkins",
        year = 1668,
        summary = "Wilkins proposed a written language whose signs name things by their place in a classification, so that reading the signs does not depend on any spoken tongue.",
        details = "The Royal Society's council prompted the project in 1662, and the Great Fire of London in 1666 damaged part of the draft. The classification had 40 top-level genera, with each lower level adding a letter to the sign, and the council called for the book to be printed in April 1668.",
    ),

)
