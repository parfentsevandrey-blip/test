package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1740 to 1750: a school for gunners, a learned society, a population office, a calendar change and a widows' fund. */
val db30Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.SYSTEM,
        section = "Military training",
        title = "The Royal Military Academy at Woolwich",
        originalTitle = "Royal Military Academy, Woolwich",
        author = "The Board of Ordnance",
        year = 1741,
        summary = "A school in which artillery and engineer officers were taught mathematics, drawing and fortification as part of their training.",
        details = "The academy opened at Woolwich in 1741 under the Board of Ordnance. Its mathematics was built into the training of artillery and engineer officers from the start, so that the calculations of gunnery were taught as a standard part of an officer's work.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.SYSTEM,
        section = "Learned societies",
        title = "The American Philosophical Society",
        originalTitle = "American Philosophical Society held at Philadelphia for Promoting Useful Knowledge",
        author = "Benjamin Franklin and others",
        year = 1743,
        summary = "A society in Philadelphia where members shared observations, experiments and inventions with each other at their meetings.",
        details = "Franklin proposed the society in 1743, and it still exists, as one of the oldest learned societies in the United States. Its early meetings were a place where colonial scientists and craftsmen could compare their results.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.SYSTEM,
        section = "Population",
        title = "Sweden's Tabellverket for population tables",
        originalTitle = "Tabellverket",
        author = "The Swedish government",
        year = 1749,
        summary = "An office that gathered the parish records of births, deaths and marriages into printed tables of the whole population.",
        details = "The office began its work in 1749 and published tables drawn from the parish registers that the Lutheran church kept. Its figures are among the earliest official national population statistics.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.SYSTEM,
        section = "Calendar",
        title = "The Calendar (New Style) Act of 1750",
        originalTitle = "An Act for regulating the Commencement of the Year; and for correcting the Calendar now in use",
        author = "The British Parliament",
        year = 1750,
        summary = "An Act that moved Britain from the Julian to the Gregorian calendar, so that the day after 2 September 1752 was 14 September.",
        details = "Parliament passed the Act in 1750, and the change took effect in 1752, when eleven days were dropped from the calendar. The Act also made 1 January the start of the legal year, which had begun on 25 March.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.SYSTEM,
        section = "Insurance",
        title = "The Scottish Ministers' Widows' Fund",
        originalTitle = "Scottish Ministers' Widows' Fund",
        author = "The ministers of the Church of Scotland",
        year = 1744,
        summary = "A fund to which ministers paid, so that their widows and children received an annual payment after a minister's death.",
        details = "The fund began in Edinburgh in 1744 and paid benefits from the contributions of its members. It was a scheme in which each minister's payments built up a fund that the families of the members could draw on.",
    ),
)
