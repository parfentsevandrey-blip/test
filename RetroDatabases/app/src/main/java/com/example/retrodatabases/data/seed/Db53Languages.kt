package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1512 to 1518: Erasmus's Greek Testament, the Complutensian Polyglot, Trithemius's ciphers, Bomberg's Hebrew Bible, an Armenian book and an Ethiopic Psalter. */
val db53Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Erasmus's Greek New Testament, the Novum Instrumentum",
        originalTitle = "Novum Instrumentum omne",
        author = "Desiderius Erasmus",
        year = 1516,
        summary = "Erasmus printed the Greek New Testament beside his own Latin translation at Basel in 1516, the first Greek New Testament to be published.",
        details = "The book came from Johann Froben's press in Basel, and Erasmus based the Greek text on a few late manuscripts with notes on his choices. The Complutensian Polyglot had printed its Greek New Testament in 1514, but that edition was not published until later, so accounts differ on which Greek text was first printed.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.LANGUAGE,
        section = "Polyglots",
        title = "The Complutensian Polyglot, four languages on one page",
        originalTitle = "Biblia Polyglotta Complutensis",
        author = "Cardinal Francisco Jimenez de Cisneros and his scholars",
        year = 1514,
        summary = "The Complutensian Polyglot set Hebrew, Greek, Latin and Aramaic side by side in parallel columns, printed at Alcala de Henares under Cardinal Cisneros.",
        details = "The New Testament was finished in January 1514, and the Old Testament volumes followed until July 1517, with the Latin Vulgate in the middle column. Papal approval came in 1520, and accounts differ on the year of publication, which is given as 1520, 1521 or 1522.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.LANGUAGE,
        section = "Ciphers",
        title = "Trithemius's Polygraphia, a printed book of ciphers",
        originalTitle = "Polygraphiae libri sex",
        author = "Johannes Trithemius",
        year = 1518,
        summary = "Trithemius's six books on secret writing, printed in 1518, set out a table of shifted alphabets that later cipher writers copied.",
        details = "The book was printed two years after the author's death in 1516, and its first book describes the tabula recta, a square of the 24 letters of the Latin alphabet with each row shifted one place. Its ciphers move the shift on from letter to letter.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Bomberg's first Rabbinic Bible, the Mikraot Gedolot",
        originalTitle = "Mikraot Gedolot",
        author = "Daniel Bomberg, edited by Felix Pratensis",
        year = 1516,
        summary = "Bomberg's Hebrew Bible, printed in Venice from 1516 to 1517, put the Hebrew text beside rabbinic commentaries on the same pages.",
        details = "Bomberg printed the edition in Venice, and Felix Pratensis, a convert from Judaism, edited it, so the commentaries of Rashi and Ibn Ezra stand around the text. The layout made one page carry the Bible and the learned readings of the rabbis together.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.LANGUAGE,
        section = "Armenian",
        title = "The Urbatagirk, the first book printed in Armenian",
        originalTitle = "Urbatagirk",
        author = "Hakob Meghapart",
        year = 1512,
        summary = "Hakob Meghapart's Urbatagirk, printed in Venice in 1512, is the first book printed in the Armenian language.",
        details = "The volume runs to 62 sheets, or 124 unnumbered pages, with red ink for its openings, and it mixes prayers and cures with extracts from the poems of Grigor Narekatsi. Accounts differ on the number of illustrations, given as 24 in one source and as four in a catalogue record for one copy, and Armenian letters had earlier appeared in a Mainz book of 1486.",
        rare = true,
    ),

    entry(
        databaseId = 53,
        kind = Kind.LANGUAGE,
        section = "Ethiopic",
        title = "Potken's Ethiopic Psalter, the first book in Ge'ez type",
        originalTitle = "Psalterium David et cantica aliqua in lingua Chaldea",
        author = "Johannes Potken, printed by Marcellus Silber",
        year = 1513,
        summary = "Johannes Potken paid for Ethiopic letters so that a Psalter could be printed in Rome in 1513, and the book is often called the first to be set in Ge'ez type.",
        details = "Silber printed the book at his press near the Campo de' Fiori, and its Latin title calls the text Chaldean although the letters are Ge'ez. The same Ethiopic letters were used again in a Cologne polyglot Psalter of 1518.",
        rare = true,
    ),

)
