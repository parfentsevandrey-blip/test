package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1810 to 1820: learned societies that pushed new notation, a university, a coinage standard and a deaf school. */
val db23Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.SYSTEM,
        section = "Societies",
        title = "The Analytical Society at Cambridge",
        originalTitle = "Analytical Society, founded at Cambridge (1812)",
        author = "Charles Babbage, John Herschel and George Peacock",
        year = 1812,
        summary = "A group of Cambridge students who campaigned to replace Newton's dot notation for derivatives with Leibniz's d notation.",
        details = "The society was founded in 1812 at Cambridge by Babbage, Herschel, Peacock and others. Its members pressed for Leibniz's notation in teaching and printed translations such as Lacroix's calculus. The record uses 1812, the year of founding.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.SYSTEM,
        section = "Standards",
        title = "The Coinage Act of 1816 and the gold sovereign",
        originalTitle = "Act for regulating the coin of the realm (1816)",
        author = "Parliament of the United Kingdom",
        year = 1816,
        summary = "A coinage act that made gold the basis of British money and authorised the sovereign, a gold coin of fixed weight.",
        details = "The act of 1816 put Britain on a gold standard, and the gold sovereign was struck from 1817. The record uses the 1816 date of the act.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "The American Asylum at Hartford for the deaf",
        originalTitle = "American Asylum for the Education and Instruction of the Deaf and Dumb (1817)",
        author = "Thomas Hopkins Gallaudet and Laurent Clerc",
        year = 1817,
        summary = "The first permanent school for deaf pupils in the United States, opened at Hartford with a deaf teacher who had taught in Paris.",
        details = "Gallaudet and Clerc opened the school in Hartford in 1817. Clerc had taught at the school for deaf pupils in Paris, and the record uses the 1817 opening year.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.SYSTEM,
        section = "Societies",
        title = "The Cambridge Philosophical Society",
        originalTitle = "Cambridge Philosophical Society, founded (1819)",
        author = "Members of the University of Cambridge",
        year = 1819,
        summary = "A Cambridge learned society for mathematics and natural philosophy, where members could read papers to one another.",
        details = "The society was founded at Cambridge in 1819 and gave its members a regular place to read papers on mathematics and natural philosophy. The record uses the 1819 founding year.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Berlin, opened 1810",
        originalTitle = "Friedrich Wilhelm University of Berlin (1810)",
        author = "Wilhelm von Humboldt and the Prussian government",
        year = 1810,
        summary = "A university opened at Berlin in 1810 on the principle that teaching and research should go together, a model that later universities copied.",
        details = "The university opened in 1810 under the Prussian reforms, and its seminars and institutes became a model for training researchers. The record uses the 1810 opening year.",
    ),
)
