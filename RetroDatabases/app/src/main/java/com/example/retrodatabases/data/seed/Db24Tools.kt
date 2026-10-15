package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1800 to 1810: a star catalogue, two bibliographies and a dictionary, an easy-date rule and two instruments. */
val db24Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.TOOL,
        section = "Star catalogues",
        title = "Piazzi's Palermo star catalogue of 1803",
        originalTitle = "Palermo star catalogue, first edition (1803)",
        author = "Giuseppe Piazzi",
        year = 1803,
        summary = "Piazzi's catalogue gave the mean positions of several thousand fixed stars, a reference table that astronomers used to find planets and comets again.",
        details = "The first edition is dated 1803, and a corrected republication followed in 1814. Accounts differ on the span of the observations, one giving 1792 to 1813 and another 1793 to 1802, so the dates of the underlying work are disputed.",
        rare = true,
    ),

    entry(
        databaseId = 24,
        kind = Kind.TOOL,
        section = "Astronomical bibliography",
        title = "Lalande's bibliography of astronomy to 1802",
        originalTitle = "Bibliographie astronomique, avec l'histoire de l'astronomie depuis 1781 jusqu'a 1802 (1803)",
        author = "Jerome Lalande",
        year = 1803,
        summary = "Lalande's bibliography listed the books and papers on astronomy published between 1781 and 1802, a finding aid for astronomers who needed the literature of their field.",
        details = "The book was printed in Paris in 1803 and gives a survey of astronomical literature for the years 1781 to 1802. It is an early example of a subject bibliography, the kind of finding aid that later catalogues of scientific papers followed.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.TOOL,
        section = "Drawing instruments",
        title = "Wollaston's camera lucida for tracing outlines",
        originalTitle = "Camera lucida, patent of 1806",
        author = "William Hyde Wollaston",
        year = 1806,
        summary = "Wollaston's camera lucida was a prism on a stand that let the draughtsman see the scene and the paper at once, so that outlines could be traced.",
        details = "Wollaston took out a patent for the instrument in 1806, and it became a standard aid for tracing outlines in the decades that followed. This record uses the patent year, and accounts differ on whether the published description or the patent should be taken as the date.",
        rare = true,
    ),

    entry(
        databaseId = 24,
        kind = Kind.TOOL,
        section = "Dictionaries",
        title = "Webster's Compendious Dictionary of the English Language",
        originalTitle = "A Compendious Dictionary of the English Language (1806)",
        author = "Noah Webster",
        year = 1806,
        summary = "Webster's one-volume dictionary of 1806 gave American spellings such as color and center, in a book sized for schools and offices.",
        details = "The dictionary was printed in the United States in 1806 and set out spellings that differed from British usage. Its American forms began a split in spelling that later American dictionaries kept.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.TOOL,
        section = "Calendar rules",
        title = "Gauss's formula for the date of Easter",
        originalTitle = "Easter formula, Monatliche Correspondenz (1800)",
        author = "Carl Friedrich Gauss",
        year = 1800,
        summary = "Gauss gave a short set of arithmetic steps that find the date of Easter for any year, without a table of the church calendar.",
        details = "Gauss's rule was published in 1800 in Zach's Monatliche Correspondenz, and it works from the remainders of the year after division by 19, 4 and 7. It is one of the early computational rules for a calendar date.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.TOOL,
        section = "Gas measurement",
        title = "Gay-Lussac and Humboldt's eudiometer for gas volumes",
        originalTitle = "Eudiometer measurement of hydrogen and oxygen by volume (1805)",
        author = "Joseph Louis Gay-Lussac and Alexander von Humboldt",
        year = 1805,
        summary = "The eudiometer was a graduated glass tube in which gases were mixed and exploded over mercury, so their volumes could be read off exactly.",
        details = "Gay-Lussac and Humboldt used the eudiometer in 1805 to show that hydrogen and oxygen combine in about two volumes to one to form water. The method was a step toward Gay-Lussac's law of combining volumes, published a few years later.",
    ),
)
