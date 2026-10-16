package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1450 to 1460: the 42-line Bible, the Mainz Psalter of 1457, Durandus's Rationale of 1459, two block books and Peuerbach's planetary theory. */
val db59Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.TOOL,
        section = "Printed books",
        title = "The 42-line Bible, printed at Mainz",
        originalTitle = "Biblia Latina",
        author = "Johann Gutenberg",
        year = 1455,
        summary = "The 42-line Bible, printed at Mainz in the 1450s from movable metal type, is the first large book printed in Europe in that way, and it is dated by estimate to about 1455.",
        details = "The book carries no date of its own, so printing is put between 1452 and 1456, with most accounts favouring 1454 to 1455. The papers of the November 1455 lawsuit between Fust and Gutenberg show the work was already under way, and they are the firmest dated evidence for its production.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.TOOL,
        section = "Printed books",
        title = "The Mainz Psalter with its printed colophon of 1457",
        originalTitle = "Psalterium Moguntinum",
        author = "Johann Fust and Peter Schoeffer",
        year = 1457,
        summary = "The Mainz Psalter of 1457 is an early printed book whose colophon names its printers and gives the date of printing as the vigil of the Assumption, 14 August 1457.",
        details = "The colophon names Johann Fust of Mainz and Peter Schoeffer of Gernsheim and refers to the vigil of the Assumption, which falls on 14 August. Accounts differ on whether the red and blue initials were printed with the black type or painted by hand, and English renderings of the colophon's claim about pen work vary.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.TOOL,
        section = "Block books",
        title = "The Ars moriendi block book, a woodcut book on dying well",
        originalTitle = "Ars moriendi",
        author = "Anonymous woodcutters",
        year = 1450,
        summary = "The Ars moriendi block book, a guide to dying well with its text and pictures cut in wood, is one of the best-known block books of the mid-fifteenth century.",
        details = "Block books printed whole pages, with the text and the pictures cut into the same wooden block, and this book is dated about 1450 in many accounts. Its place of cutting is not settled.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.TOOL,
        section = "Block books",
        title = "The Biblia pauperum, the Poor Man's Bible as a block book",
        originalTitle = "Biblia pauperum",
        author = "Anonymous compilers",
        year = 1460,
        summary = "The Biblia pauperum, a block book whose pictures are linked by explanatory texts, survives in leaves that catalogues date to about 1460.",
        details = "Catalogue dates for single leaves run to about 1460 and 1465, so the year used here is the earliest of them and the date of the whole edition is not settled. Manuscript copies of the text survive from about 1300, and the title is considered inaccurate for the book.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.TOOL,
        section = "Liturgy",
        title = "Durandus's Rationale, printed at Mainz by Fust and Schoeffer in 1459",
        originalTitle = "Rationale divinorum officiorum",
        author = "Guillelmus Durandus, bishop of Mende",
        year = 1459,
        summary = "Durandus's Rationale divinorum officiorum, a guide to church buildings, vestments and the Mass, was printed at Mainz by Fust and Schoeffer in 1459.",
        details = "The printers' edition is dated 6 October 1459, and it is one of the books that Fust and Schoeffer printed after their Psalter of 1457. Surviving copies are numerous, most of them on vellum, and the text was printed many times before 1501.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Peuerbach's Theoricae novae planetarum, a textbook of planetary theory",
        originalTitle = "Theoricae novae planetarum",
        author = "Georg von Peuerbach",
        year = 1454,
        summary = "Peuerbach's Theoricae novae planetarum, a textbook of planetary theory taught at Vienna, was written in the 1450s and first printed in 1472.",
        details = "The date used here, 1454, is an estimate for the teaching text, and the printing of 1472 is named because it is the first known print. The text sets out the planetary models that were taught in Vienna in the mid-fifteenth century.",
    ),

)
