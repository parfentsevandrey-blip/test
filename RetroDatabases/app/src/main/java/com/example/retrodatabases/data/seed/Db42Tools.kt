package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1620 to 1630: Bacon's method, Briggs's and Vlacq's logarithm tables, Kepler's planetary tables, a Japanese arithmetic book and Harvey's circulation of the blood. */
val db42Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.TOOL,
        section = "Method",
        title = "Bacon's Novum Organum",
        originalTitle = "Novum Organum scientiarum",
        author = "Francis Bacon",
        year = 1620,
        summary = "Bacon's Novum Organum of 1620 sets out a method of collecting cases into tables and testing them, offered in place of the syllogism.",
        details = "Bacon printed the book in Latin in 1620 as the second part of his Great Instauration. Its method uses tables of presence, of absence and of degrees to sort the instances of a phenomenon, and the guide in this batch runs through one of those tables.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.TOOL,
        section = "Logarithms",
        title = "Briggs's Arithmetica logarithmica of 1624",
        originalTitle = "Arithmetica logarithmica",
        author = "Henry Briggs",
        year = 1624,
        summary = "Briggs's table of common logarithms, printed in London in 1624, gives the logs of numbers to fourteen decimal places over two ranges.",
        details = "The book gives logarithms of 1 to 20000 and of 90000 to 100000 at fourteen decimal places. Its sixth chapter computes them by chained square roots of ten, and the characteristic has a chapter of its own. The range that Briggs left out was filled by Vlacq in 1628.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.TOOL,
        section = "Logarithms",
        title = "Vlacq's logarithm tables of 1628",
        originalTitle = "Arithmetica logarithmica",
        author = "Adriaan Vlacq",
        year = 1628,
        summary = "Vlacq's tables, printed at Gouda in 1628, filled the gap in Briggs's table and gave logarithms of every number up to 100000 at ten decimal places.",
        details = "The Gouda printing of 1628 added the range from 20000 to 90000 that Briggs had left out. It gives ten decimal places, against fourteen in Briggs, and it also lists trigonometric logarithms to each minute of arc.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Kepler's Rudolphine Tables",
        originalTitle = "Tabulae Rudolphinae",
        author = "Johannes Kepler",
        year = 1627,
        summary = "Kepler's planetary tables, printed at Ulm in 1627 and named for Emperor Rudolf II, give planetary positions computed with logarithms.",
        details = "The tables were printed at Ulm in 1627 and named for the emperor Rudolf II. They used logarithms to compute planetary positions, which made them a computing tool of the period, and astronomers used them for decades.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Yoshida's Jinkoki, the Japanese arithmetic book",
        originalTitle = "Jinkoki",
        author = "Yoshida Mitsuyoshi",
        year = 1627,
        summary = "The Jinkoki of 1627 is a Japanese arithmetic textbook of worked problems, set out for calculating with the abacus, and it was read for generations.",
        details = "Yoshida's book teaches arithmetic through worked problems that use the abacus, and it was reprinted and copied in Japan for a long time. Its problems are the subject of the practice record in this batch.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.TOOL,
        section = "Physiology",
        title = "Harvey's De motu cordis",
        originalTitle = "Exercitatio anatomica de motu cordis et sanguinis in animalibus",
        author = "William Harvey",
        year = 1628,
        summary = "Harvey's book of 1628 argues that the blood circulates round the body and that the heart pumps out, in short periods, more blood than the body holds.",
        details = "The book was printed at Frankfurt in 1628. Its half-hour argument, that the heart pumps in half an hour more blood than the body contains, is the quantitative case that the guide in this batch works through with illustrative figures.",
    ),

)
