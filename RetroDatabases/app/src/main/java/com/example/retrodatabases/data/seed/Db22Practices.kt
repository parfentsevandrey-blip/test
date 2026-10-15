package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1820 to 1830: experiments, societies, a periodical and an education movement of the decade. */
val db22Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.PRACTICE,
        section = "Electromagnetism",
        title = "Oersted's compass-needle test of 1820",
        originalTitle = "Experimenta circa effectum conflictus electrici in acum magneticam (1820)",
        author = "Hans Christian Oersted",
        year = 1820,
        summary = "A wire carrying current set beside a compass needle turned the needle, which showed that electricity and magnetism act on each other.",
        details = "Oersted had found the effect during lecture demonstrations earlier in 1820, and he described the test in a Latin note printed in July 1820. The test became the standard way to show that a current has a magnetic effect.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.PRACTICE,
        section = "Societies",
        title = "Royal Astronomical Society, founded 1820",
        originalTitle = "Astronomical Society of London (1820)",
        author = "Francis Baily and others",
        year = 1820,
        summary = "A London society founded in 1820 to improve astronomical tables and the calculations that depend on them.",
        details = "It began as the Astronomical Society of London, to correct and publish the tables that astronomers and navigators used. It received a royal charter in 1831, which gave it the name it keeps.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.PRACTICE,
        section = "Education",
        title = "London Mechanics' Institution",
        originalTitle = "London Mechanics' Institution (1823)",
        author = "George Birkbeck and others",
        year = 1823,
        summary = "A London institute founded in 1823 to teach working men science through lectures, classes and a library.",
        details = "George Birkbeck, a physician, led the founding, and the institution later took his name as Birkbeck College. Similar mechanics' institutes spread to other British towns within a few years.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.PRACTICE,
        section = "Periodicals",
        title = "Journal of the Franklin Institute",
        originalTitle = "Journal of the Franklin Institute (1826)",
        author = "Franklin Institute of Pennsylvania",
        year = 1826,
        summary = "A Philadelphia periodical founded in 1826 to print reports of mechanical and scientific work for practical men.",
        details = "The journal began in 1826 with articles on engines, chemistry and the working arts, and it kept that practical bent through the century.",
        rare = true,
    ),

    entry(
        databaseId = 22,
        kind = Kind.PRACTICE,
        section = "Education",
        title = "Society for the Diffusion of Useful Knowledge",
        originalTitle = "Society for the Diffusion of Useful Knowledge",
        author = "Henry Brougham and others",
        year = 1826,
        summary = "A London society founded in 1826 to publish cheap and accurate books on science and the useful arts for ordinary readers.",
        details = "Henry Brougham led the society, and it published tracts for working people, followed from 1833 by the Penny Cyclopaedia. Its cheap series aimed to spread knowledge beyond the readers who could afford expensive books.",
    ),
)
