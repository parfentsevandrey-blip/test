package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1760 to 1770: encyclopedias and plates, a chart of lives, calculus and astronomy textbooks and a set of essays. */
val db28Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.TOOL,
        section = "Encyclopedias",
        title = "Diderot's Encyclopedie and its engraved plates",
        originalTitle = "Encyclopedie, ou Dictionnaire raisonne des sciences, des arts et des metiers",
        author = "Denis Diderot and Jean le Rond d'Alembert",
        year = 1762,
        summary = "A reference work of articles and engraved plates that set out the tools and processes of the crafts, with the plates appearing from 1762.",
        details = "The Encyclopedie was edited by Diderot and d'Alembert, and its volumes of text began in 1751. The plates, which show machines and workshops in engraved detail, were issued in volumes from 1762 to 1772. The engravings are a main source for how the eighteenth-century trades made and used their tools.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.TOOL,
        section = "Encyclopedias",
        title = "Macquer's Dictionnaire de chymie",
        originalTitle = "Dictionnaire de chymie",
        author = "Pierre Joseph Macquer",
        year = 1766,
        summary = "A two-volume chemical dictionary that gathered the chemistry of its day in alphabetical articles and was used as a textbook.",
        details = "Macquer, a French chemist, published the dictionary in 1766 in two volumes. It arranged the chemistry of the period in alphabetical articles and was widely used as a textbook. Lavoisier's nomenclature later revised many of the names it used.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.TOOL,
        section = "Charts",
        title = "Priestley's Chart of Biography",
        originalTitle = "A Chart of Biography",
        author = "Joseph Priestley",
        year = 1765,
        summary = "A chart that draws the lives of famous people as bars against a timeline, one of the earliest charts to show history this way.",
        details = "Priestley published the chart in 1765 and later made charts of history in the same style. Each person is a bar running from birth to death, so the lengths and overlaps show who lived when. Historians of graphics count it among the earliest timelines.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.TOOL,
        section = "Calculus",
        title = "Euler's Institutiones calculi integralis",
        originalTitle = "Institutionum calculi integralis",
        author = "Leonhard Euler",
        year = 1768,
        summary = "A three-volume Latin textbook of integral calculus, which set out methods for differential equations that later courses kept.",
        details = "The first volume appeared in 1768 and the later volumes followed by 1770. It collects methods for ordinary and partial differential equations, and it includes the step method for ordinary equations that bears Euler's name.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Lalande's Traite d'astronomie",
        originalTitle = "Traite d'astronomie",
        author = "Joseph Jerome Lefrancois de Lalande",
        year = 1764,
        summary = "A French textbook of astronomy that gathered the observations and methods of its period into one work for students.",
        details = "Lalande, a Paris astronomer, published the textbook in 1764. It covered observation, instruments and the calculation of orbits, and it was revised in later editions.",
        rare = true,
    ),

    entry(
        databaseId = 28,
        kind = Kind.TOOL,
        section = "Essays",
        title = "Lambert's Beytraege zum Gebrauche der Mathematik",
        originalTitle = "Beytraege zum Gebrauche der Mathematik und deren Anwendung",
        author = "Johann Heinrich Lambert",
        year = 1765,
        summary = "A collection of Lambert's essays on mathematics and its uses, issued in parts from 1765 onward.",
        details = "Lambert issued the essays in several parts. They cover mathematics and its use in practical problems, and they show his methods for series and approximations.",
        rare = true,
    ),
)
