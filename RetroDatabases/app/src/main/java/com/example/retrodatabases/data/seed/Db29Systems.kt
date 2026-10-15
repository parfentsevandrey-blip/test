package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1750 to 1760: a public museum, a colonial post, a university, two learned bodies for sea and science. */
val db29Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The British Museum founded by Act of Parliament",
        originalTitle = "British Museum Act 1753",
        author = "Parliament of Great Britain",
        year = 1753,
        summary = "A public museum and library created by Act of Parliament in 1753, which bought Sir Hans Sloane's collection and joined it to the Cotton and Harley manuscripts.",
        details = "The Act made the collections the property of the nation and set up trustees to run them. The museum opened to the public in 1759 at Montagu House, and the trustees were charged with keeping the collections open to scholars.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.SYSTEM,
        section = "Post",
        title = "Franklin and Hunter's joint post of British America",
        originalTitle = "Joint deputy postmasters general for North America",
        author = "Benjamin Franklin and William Hunter",
        year = 1753,
        summary = "In 1753 two men were made joint postmasters of the British colonies in North America, which put the colonial mail under one management.",
        details = "Franklin and William Hunter were appointed joint deputy postmasters general for North America in 1753. Franklin reorganised the routes and the accounts, which made the colonial post a single system.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "Moscow University founded by imperial decree",
        originalTitle = "Moscow University",
        author = "Empress Elizabeth, with Ivan Shuvalov and Mikhail Lomonosov",
        year = 1755,
        summary = "A university founded at Moscow in 1755 by imperial decree, on a plan that Lomonosov helped to shape.",
        details = "The decree of January 1755 set up the university, which became a centre of Russian science and teaching. Lomonosov helped to draft its plan, and the university grew into one of the main schools of Russia.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.SYSTEM,
        section = "Learned societies",
        title = "The Holland Society of Sciences at Haarlem",
        originalTitle = "Hollandsche Maatschappij der Wetenschappen",
        author = "Learned men of Holland",
        year = 1752,
        summary = "A learned society founded at Haarlem in 1752, which ran prize questions and published the work of its members.",
        details = "The society was founded at Haarlem in 1752 and set prize questions that drew essays from across Europe. Its published treatises were a steady record of experiment in the Netherlands.",
        rare = true,
    ),

    entry(
        databaseId = 29,
        kind = Kind.SYSTEM,
        section = "Naval science",
        title = "The Academie de marine at Brest",
        originalTitle = "Academie de marine",
        author = "The French navy and its officers",
        year = 1752,
        summary = "A naval academy founded at Brest in 1752 to teach navigation and naval science to officers.",
        details = "The academy was set up in 1752 at the French naval port of Brest, where its members worked on navigation, instruments and charts. It was one of the places where officers learned the mathematics of the sea.",
    ),

)
