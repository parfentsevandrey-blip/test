package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1920 to 1930: a table of random digits, slide rules, a statistics book, a forecasting book and a cryptanalysis monograph. */
val db12Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.TOOL,
        section = "Tables",
        title = "Tippett's random sampling numbers",
        originalTitle = "Random Sampling Numbers (Tippett, 1927)",
        author = "L. H. C. Tippett",
        year = 1927,
        summary = "A printed table of 41,600 random digits, taken from census figures, for drawing samples by hand in 1927.",
        details = "Several sources give the count as 41,600 digits, though one teaching file says 40,000. Accounts say the digits came from census material, and that the table was published by Cambridge University Press. The 1939 table of Kendall and Babington Smith has a similar title and is a later, larger work. The table was an early answer to the need for random numbers that were not made by a formula.",
        rare = true,
    ),

    entry(
        databaseId = 12,
        kind = Kind.TOOL,
        section = "Calculators",
        title = "Otis King's cylindrical slide rule",
        originalTitle = "Otis King's Patent Calculator, made by Carbic (1922)",
        author = "Otis Carter Formby King",
        year = 1922,
        summary = "A pocket cylindrical slide rule with a spiral scale, made in London by Carbic from about 1922.",
        details = "The British patent 183,723 is dated 1921 by some lists and 1922 by others, so the date is disputed. Carbic Ltd began production about 1922, and museum examples carry that patent number. Wikipedia credits King with the spiral-scale cylinder design. A cylinder rule of this kind put a long scale in a small pocket tool.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.TOOL,
        section = "Statistics",
        title = "Fisher's Statistical Methods for Research Workers",
        originalTitle = "Statistical Methods for Research Workers (Fisher, 1925)",
        author = "R. A. Fisher",
        year = 1925,
        summary = "The book that made the point where P is .05 a convenient limit for judging significance, and that taught researchers a set of statistical tests.",
        details = "The 1925 book calls the point where P is .05, about 1.96 standard deviations, convenient as the limit for judging whether a deviation is significant. The phrase 5 per cent comes from Fisher's later writings, and one source dates a similar line to a 1926 paper. Page numbers differ between editions.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.TOOL,
        section = "Forecasting",
        title = "Richardson's forecast factory",
        originalTitle = "Weather Prediction by Numerical Process (Richardson, 1922)",
        author = "Lewis Fry Richardson",
        year = 1922,
        summary = "The book that set out a numerical weather forecast as a grid of cells, and pictured a forecast factory of human computers.",
        details = "Published in 1922, the book stores the key variables of the atmosphere in each cell of a grid covering the globe. Its last chapter imagines a forecast factory of human computers working faster than the weather. Richardson counted about 64,000 people needed to keep pace, and a later commentator argues the figure is too low. The book was reissued by Dover in 1965 and by Cambridge University Press in 2007.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.TOOL,
        section = "Calculators",
        title = "Darmstadt slide rule layout",
        originalTitle = "Darmstadt layout, from Dennert and Pape (1924)",
        author = "Dennert and Pape",
        year = 1924,
        summary = "A slide-rule layout that put log-log and P scales on the slide, launched by the firm Dennert and Pape in 1924.",
        details = "The design drew on Alwin Walther's scale arrangement and descends from Max Rietz's 1902 reworking of the Mannheim layout. A 1934 engineering version from the Technische Hochschule Darmstadt, called System Darmstadt, added a Pythagorean scale. Some gallery listings give 1924 for the launch and a Berlin history page gives 1934, so the date is disputed.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.TOOL,
        section = "Cryptanalysis",
        title = "Friedman's index of coincidence",
        originalTitle = "The Index of Coincidence and Its Applications in Cryptography (Riverbank Publication No. 22, 1922)",
        author = "William F. Friedman",
        year = 1922,
        summary = "The Riverbank publication that introduced the index of coincidence, a statistic that measures how unevenly the letters of a text are spread.",
        details = "Riverbank Publication No. 22 introduced the index of coincidence as a tool for cryptanalysis, and historian David Kahn called it the most important single publication in cryptography. Kahn dates its writing to 1920, when Friedman was 28, so the year of publication and the year of writing differ. The French edition carries a date that one source calls wrong, so cite the English imprint of 1922. The series was produced at Riverbank Laboratories in Geneva, Illinois.",
    ),
)
