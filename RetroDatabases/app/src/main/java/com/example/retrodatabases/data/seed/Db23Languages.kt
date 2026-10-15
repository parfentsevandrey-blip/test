package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1810 to 1820: chemical and calculus notation, a comparative grammar, a notation pamphlet, decipherment and a signal code. */
val db23Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.LANGUAGE,
        section = "Chemical notation",
        title = "Berzelius's chemical symbols",
        originalTitle = "Essay on chemical proportions, five parts in the Annals of Philosophy (1813 to 1814)",
        author = "Jons Jacob Berzelius",
        year = 1813,
        summary = "Berzelius proposed writing each element as one or two letters taken from its name, a scheme that became the modern chemical notation.",
        details = "Berzelius set out the letter symbols in 1813, in a series that ran in the Annals of Philosophy until 1814, so the record uses 1813 for the first printed proposal. A later teaching resource places the essay in 1814, and that date dispute is named here.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.LANGUAGE,
        section = "Calculus notation",
        title = "Lacroix's calculus in Leibniz's dx notation",
        originalTitle = "Elementary Treatise on the Differential and Integral Calculus, English translation (1816)",
        author = "Sylvestre Francois Lacroix, translated by Babbage, Herschel and Peacock",
        year = 1816,
        summary = "The Cambridge translation of Lacroix's textbook writes derivatives with Leibniz's dx and dy in place of Newton's dots.",
        details = "Charles Babbage, John Herschel and George Peacock, members of the Analytical Society, printed the translation at Cambridge in 1816. Its use of Leibniz's notation supported the society's campaign against Newton's dot notation, and the record uses the 1816 printing date.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.LANGUAGE,
        section = "Comparative grammar",
        title = "Bopp's comparison of verb conjugations",
        originalTitle = "Uber das Conjugationssystem der Sanskritsprache in Vergleichung mit jenem der griechischen, lateinischen, persischen und germanischen Sprache (1816)",
        author = "Franz Bopp",
        year = 1816,
        summary = "Bopp set the Sanskrit verb endings beside those of Greek, Latin, Persian and German and showed systematic correspondences between them.",
        details = "The 1816 book is usually counted as an early work of comparative Indo-European grammar. It compares verb forms across languages rather than proposing a notation, and the record uses the 1816 date of the book.",
        rare = true,
    ),

    entry(
        databaseId = 23,
        kind = Kind.LANGUAGE,
        section = "Calculus notation",
        title = "Babbage's Principles of Pure D-ism",
        originalTitle = "The Principles of Pure D-ism in Opposition to the Dogmas of the Triangle (1815)",
        author = "Charles Babbage",
        year = 1815,
        summary = "A printed case by Babbage for Leibniz's d notation in place of Newton's dots.",
        details = "Babbage's pamphlet of 1815 was one of the Analytical Society's printed statements in favour of Leibniz's notation. The record uses the 1815 date of the pamphlet.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.LANGUAGE,
        section = "Decipherment",
        title = "Young's demotic sign values",
        originalTitle = "Egypt, article in the Supplement to the Encyclopaedia Britannica (1819)",
        author = "Thomas Young",
        year = 1819,
        summary = "Young matched several demotic signs with sound values and linked the demotic script to the hieroglyphs.",
        details = "The article on Egypt in the 1819 Britannica supplement was unsigned and was later credited to Young. Several of his sound values were wrong, and the record uses the 1819 printing date.",
        rare = true,
    ),

    entry(
        databaseId = 23,
        kind = Kind.LANGUAGE,
        section = "Signal codes",
        title = "Marryat's code of signals for merchant ships",
        originalTitle = "Code of signals for merchant shipping (1817)",
        author = "Frederick Marryat",
        year = 1817,
        summary = "A signal book in which a group of digits stands for a whole phrase, so that ships could send messages with flags.",
        details = "The 1817 volume is treated as an early broad signalling code for merchant shipping, and it was reissued later under other names. The record uses the 1817 date of the volume, and how far it drew on Popham's earlier naval code is disputed.",
        rare = true,
    ),
)
