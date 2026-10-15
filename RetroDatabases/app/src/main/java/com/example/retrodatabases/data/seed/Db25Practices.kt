package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1790 to 1800: human computing, shorthand records of lectures, a national count, stone printing and dating by the Republic. */
val db25Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.PRACTICE,
        section = "Computing",
        title = "Prony's computing office",
        originalTitle = "Bureau de calcul for the cadastre (1792 to 1799)",
        author = "Gaspard de Prony",
        year = 1792,
        summary = "A team of human computers in Paris split the work of calculating logarithm and trigonometric tables into simple steps, as a factory divides its work.",
        details = "Prony set up the office in 1792 for the land survey, and it ran until about 1799. Its workers were often men with little training, some of them former hairdressers, and they built the tables with the method of differences.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.PRACTICE,
        section = "Recording",
        title = "Stenographers at the Ecole normale",
        originalTitle = "Seances des Ecoles normales (1800)",
        author = "Stenographers of the Ecole normale",
        year = 1795,
        summary = "Shorthand writers took down the lectures of 1795 word for word, and the notes were printed later with the lecturers' corrections.",
        details = "The lectures at the Ecole normale in 1795 were recorded by stenographers, and the transcripts were printed from 1800 as the Seances des Ecoles normales. The book shows an early use of shorthand to keep a near-verbatim record of teaching.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.PRACTICE,
        section = "Counting",
        title = "The first US census count",
        originalTitle = "An Act providing for the enumeration of the inhabitants (1790)",
        author = "Congress of the United States",
        year = 1790,
        summary = "Federal marshals counted every household in their districts and tallied people by age, sex and status, to produce the first national census.",
        details = "The census act was passed on 1 March 1790, and the count was made that year by marshals in each district. The district returns were added up and sent to the seat of government, where the totals were reported to Congress.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.PRACTICE,
        section = "Printing",
        title = "Printing from a stone",
        originalTitle = "Vollstandiges Lehrbuch der Steindruckerey (1818)",
        author = "Alois Senefelder",
        year = 1796,
        summary = "Greasy ink drawn on a smooth stone and kept moist with water printed sharp copies, because the wet stone repelled the ink except where it had been drawn.",
        details = "Senefelder found the process in Munich in 1796 while looking for a cheap way to print a text. He described it later in a manual of 1818, and the method spread through Europe in the following decades.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.PRACTICE,
        section = "Dating",
        title = "Dating documents by the Republican year",
        originalTitle = "Official dating in the Republican calendar (1793)",
        author = "French National Convention",
        year = 1793,
        summary = "Official papers in France carried the year of the Republic, such as year II, along with the day and month of the new calendar.",
        details = "Papers of the 1790s and early 1800s were dated by Republican year, month and day, and the practice ended when the Gregorian calendar was restored on 1 January 1806. Clerks and printers had to learn the new month names and the ten-day weeks.",
    ),
)
