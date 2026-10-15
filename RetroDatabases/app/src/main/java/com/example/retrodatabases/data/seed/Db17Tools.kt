package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1870 to 1880: linkages, knot tables, thermodynamic diagrams, heliograph, stencil pen and factor tables. */
val db17Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.TOOL,
        section = "Linkages",
        title = "Kempe's linkage for a straight line",
        originalTitle = "How to Draw a Straight Line (1877)",
        author = "Alfred Bray Kempe",
        year = 1877,
        summary = "A jointed frame of pivoted bars that draws a straight line exactly, with no guide rail.",
        details = "Kempe's 1877 book How to Draw a Straight Line explained how a set of pivoted bars could trace a straight line. Kempe had earlier written on linkages that trace plane curves of higher degree.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.TOOL,
        section = "Knot tables",
        title = "Tait's tables of knots",
        originalTitle = "On knots",
        author = "Peter Guthrie Tait",
        year = 1877,
        summary = "A table of knot diagrams grouped by the number of crossings, an early systematic list of knots.",
        details = "Tait built his tables from drawings of knots in the 1870s, arranging the knots by the number of crossings in a plain diagram. The main table is given in his paper On Knots of 1877.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.TOOL,
        section = "Thermodynamic diagrams",
        title = "Gibbs's graphical method for thermodynamic surfaces",
        originalTitle = "Graphical methods in the thermodynamics of fluids (1873)",
        author = "Josiah Willard Gibbs",
        year = 1873,
        summary = "A way of drawing the energy, entropy and volume of a substance as a surface, so that its states and phases read off as points and lines on it.",
        details = "Gibbs published the method in 1873. Maxwell later made a plaster model of the surface for water from Gibbs's diagrams.",
        rare = true,
    ),

    entry(
        databaseId = 17,
        kind = Kind.TOOL,
        section = "Signalling",
        title = "Mance's heliograph",
        originalTitle = "Heliograph",
        author = "Henry Christopher Mance",
        year = 1875,
        summary = "A mirror instrument that flashed sunlight in coded signals over long distances, and which British forces in India used in the 1870s.",
        details = "Mance's heliograph was formally approved for use in British India in 1875, and it was first used in war in 1877. Accounts give the invention as about 1869, and this record uses 1875, the year of the first formal approval.",
        rare = true,
    ),

    entry(
        databaseId = 17,
        kind = Kind.TOOL,
        section = "Duplicating",
        title = "Edison's electric pen",
        originalTitle = "Improvement in autographic printing",
        author = "Thomas Alva Edison",
        year = 1876,
        summary = "A pen driven by a magnet that cut a stencil with a rapidly moving needle, from which a press could print many copies.",
        details = "Edison's notes date the stencil idea to 1875, and his patent for autographic printing was granted in 1876. Later duplicating machines used the same kind of stencil, so the pen is an ancestor of the mimeograph.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.TOOL,
        section = "Tables of factors",
        title = "Glaisher's factor tables",
        originalTitle = "Factor table for the fourth million (1879)",
        author = "J. W. L. Glaisher",
        year = 1879,
        summary = "A printed table that gives the smallest prime factor of each number in a range of one million, so that a number can be factored by lookup.",
        details = "The fourth-million volume was published in London in 1879, the fifth in 1880 and the sixth in 1883. Catalogues disagree on the author, and some list James Glaisher, the meteorologist, while the factor tables are generally credited to J. W. L. Glaisher.",
    ),
)
