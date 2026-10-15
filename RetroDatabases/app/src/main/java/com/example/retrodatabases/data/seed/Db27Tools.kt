package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1770 to 1780: algebra books, an encyclopedia, a Paris academy volume and a book of gas experiments. */
val db27Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Waring's Meditationes Algebraicae",
        originalTitle = "Meditationes Algebraicae",
        author = "Edward Waring",
        year = 1770,
        summary = "A Latin book of algebra that stated Waring's problem and gave Wilson's theorem without a proof.",
        details = "Waring printed the book at Cambridge in 1770, and it claims that every whole number is a sum of nine cubes, which is now called Waring's problem. The same book states the result now called Wilson's theorem, credited to his pupil John Wilson, without a demonstration. Accounts differ on whether Waring himself could have proved it.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Bezout's general theory of algebraic equations",
        originalTitle = "Theorie generale des equations algebriques",
        author = "Etienne Bezout",
        year = 1779,
        summary = "A treatise on algebraic equations that set out the elimination of unknowns and the count of common solutions of two polynomial equations.",
        details = "Bezout published the book in Paris in 1779. It treats the elimination of unknowns and gives the number of common points of two plane curves, a result now called Bezout's theorem. Accounts differ on how complete his proof was, and later writers corrected parts of it.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Euler's complete introduction to algebra",
        originalTitle = "Vollstaendige Anleitung zur Algebra",
        author = "Leonhard Euler",
        year = 1770,
        summary = "A two-part algebra textbook written for a general reader, which set out the methods for solving equations.",
        details = "Euler published the textbook in German at St Petersburg in 1770. It covers the solution of equations, the rules of algebra and the basic facts about whole numbers. An English version followed much later, in 1797.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.TOOL,
        section = "Reference",
        title = "The first Encyclopaedia Britannica",
        originalTitle = "Encyclopaedia Britannica",
        author = "William Smellie, editor",
        year = 1771,
        summary = "A general encyclopedia printed in Edinburgh in three volumes, which later grew into one of the best known reference works.",
        details = "The first edition appeared in parts in Edinburgh between 1768 and 1771, and the last volume is usually dated to 1771. William Smellie edited it and drew its articles from earlier reference works. Accounts differ on the number of plates and on the printing dates of the separate parts.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.TOOL,
        section = "Chemistry",
        title = "Priestley's experiments on different kinds of air",
        originalTitle = "Experiments and Observations on Different Kinds of Air",
        author = "Joseph Priestley",
        year = 1774,
        summary = "Priestley's multi-volume report on the gases he had prepared, including the air he called dephlogisticated.",
        details = "The first volume came out in 1774, and the later volumes followed in 1775 and 1777. Scheele had prepared the same gas in Sweden in the early 1770s, and the priority for it is disputed. The volumes are dated here by the first printing.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.TOOL,
        section = "Academy volumes",
        title = "The Paris Academy's yearly volume for 1772",
        originalTitle = "Histoire de l'Academie royale des sciences, annee 1772",
        author = "Academie royale des sciences, Paris",
        year = 1772,
        summary = "The Paris Academy's yearly volume for 1772, which holds Vandermonde's memoir on elimination.",
        details = "Most catalogues date the volume to 1772, though some give its printing as 1776. Vandermonde's memoir fills pages 516 to 532, and its first reading at the Academy is dated to January 1771. The memoir is cited here to the year of the volume.",
    ),

)
