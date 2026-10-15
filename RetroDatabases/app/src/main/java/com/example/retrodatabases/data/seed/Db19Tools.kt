package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1850 to 1860: a slide rule, a planimeter, a linkage, two sets of logarithm tables and a calculating machine. */
val db19Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.TOOL,
        section = "Slide rules",
        title = "Mannheim slide rule",
        originalTitle = "Slide rule with the Mannheim scale layout (1850)",
        author = "Amedee Mannheim",
        year = 1850,
        summary = "The Mannheim slide rule fixed the scales in a layout with a sliding centre scale and a runner, and that layout became the common pattern for most of the next century.",
        details = "Amedee Mannheim, a French artillery officer, is credited with the layout of 1850. The Mannheim pattern was copied widely and remained the common layout into the twentieth century.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.TOOL,
        section = "Measuring instruments",
        title = "Amsler's polar planimeter",
        originalTitle = "Polarplanimeter (1854)",
        author = "Jakob Amsler",
        year = 1854,
        summary = "The polar planimeter traces the outline of a plane figure with a pointer and reads off the enclosed area from a wheel, using a jointed arm.",
        details = "Amsler designed the instrument in 1854, and it became a standard tool for measuring areas on maps and drawings. The wheel counts the area swept by the arm as the pointer goes round the outline.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.TOOL,
        section = "Linkages",
        title = "Chebyshev's parallelogram linkage",
        originalTitle = "Theorie des mecanismes connus sous le nom de parallelogrammes (1854)",
        author = "Pafnuty Chebyshev",
        year = 1854,
        summary = "A jointed four-bar linkage in which one point traces a path that is nearly straight over part of its motion.",
        details = "Chebyshev analysed linkages of this kind in his 1854 memoir and showed how their coupler points approximate straight-line motion. The mechanism was one of the first to be designed by calculation rather than by trial.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.TOOL,
        section = "Logarithm tables",
        title = "Vega's logarithm handbook, revised by Bremiker",
        originalTitle = "Logarithmisch-trigonometrisches Handbuch (Vega)",
        author = "Georg von Vega, revised by Karl Bremiker",
        year = 1856,
        summary = "Vega's handbook of logarithmic and trigonometric tables was revised by Karl Bremiker, who held the work by 1856.",
        details = "Vega first published the tables in the late eighteenth century, and later editions were revised by Bremiker. Editions differ in their number of decimal places, and the English translations followed different German editions.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.TOOL,
        section = "Logarithm tables",
        title = "Schron's seven-figure logarithm tables",
        originalTitle = "Siebenstellige Tafeln der Logarithmen (1860)",
        author = "Ludwig Schron",
        year = 1860,
        summary = "Schron's seven-figure tables gave logarithms to seven places, the precision that navigators, surveyors and telegraph engineers needed for long calculations.",
        details = "Seven-figure tables were a step up from the five- and six-figure tables of the same period. Such tables were used in long chains of calculation, where small rounding errors would otherwise build up.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.TOOL,
        section = "Calculating machines",
        title = "Thomas de Colmar's arithmometer",
        originalTitle = "Arithmometre",
        author = "Charles Xavier Thomas de Colmar",
        year = 1851,
        summary = "The arithmometer was a desktop calculator that multiplied and divided with a stepped drum, and it was among the first calculators sold in quantity.",
        details = "Thomas patented the stepped-drum mechanism in 1820 and sold the machine commercially from 1851. The design stayed in production into the twentieth century, and thousands were made.",
    ),
)
