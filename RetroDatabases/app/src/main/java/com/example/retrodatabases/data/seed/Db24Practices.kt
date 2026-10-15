package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1800 to 1810: fitting observations by least squares, finding a lost planet, counting a nation and splitting water. */
val db24Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.PRACTICE,
        section = "Least squares",
        title = "Least squares for fitting a comet's orbit, Legendre 1805",
        originalTitle = "Nouvelles methodes pour la determination des orbites des cometes, with the appendix on least squares (1805)",
        author = "Adrien-Marie Legendre",
        year = 1805,
        summary = "Legendre printed the method of least squares, which chooses the fit that makes the sum of squared errors as small as possible, for orbits computed from noisy observations.",
        details = "The method appeared in 1805 as an appendix to Legendre's book on the orbits of comets, which gave it a printed form. Gauss claimed to have used the method from about 1795, and the priority dispute with Legendre is part of its history.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.PRACTICE,
        section = "Orbit determination",
        title = "Gauss's recovery of the lost planet Ceres in 1801",
        originalTitle = "Orbit of Ceres computed from a few observations (1801)",
        author = "Carl Friedrich Gauss",
        year = 1801,
        summary = "Gauss computed the orbit of the newly found planet Ceres from a few observations, and his prediction let astronomers find it again at the end of 1801.",
        details = "Piazzi found Ceres on 1 January 1801 and lost it in the glare of the sun. Gauss's orbit gave the place where it should appear, and astronomers found it again at the end of that year, while the full method was printed later in Gauss's Theoria motus of 1809.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.PRACTICE,
        section = "Population counts",
        title = "The first British census, taken in 1801",
        originalTitle = "Census Act of 1800 and the enumeration of 1801",
        author = "The British Parliament and the parish enumerators",
        year = 1801,
        summary = "Britain counted its population parish by parish in 1801, in the first national census, with local officials returning their totals to London.",
        details = "The Census Act of 1800 required parish officers to count people and houses, and the count was taken in March 1801. The totals were published from London, and the act set the pattern for the census that followed every ten years.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.PRACTICE,
        section = "Adult education",
        title = "Birkbeck's free lectures for Glasgow artisans",
        originalTitle = "Lectures on mechanics for the artisans of Glasgow, Anderson's Institution (1800)",
        author = "George Birkbeck",
        year = 1800,
        summary = "Birkbeck gave free lectures on mechanics and chemistry to artisans in Glasgow, a practice that later spread through the mechanics' institutes of Britain.",
        details = "Birkbeck began the lectures at Anderson's Institution in Glasgow in 1800, to teach working men the principles of the machines they used. The practice led to the mechanics' institutes, which spread through Britain in the following decades.",
        rare = true,
    ),

    entry(
        databaseId = 24,
        kind = Kind.PRACTICE,
        section = "Electrochemistry",
        title = "Splitting water by a voltaic pile, Nicholson and Carlisle",
        originalTitle = "Account of the decomposition of water by voltaic electricity (1800)",
        author = "William Nicholson and Anthony Carlisle",
        year = 1800,
        summary = "Nicholson and Carlisle used a voltaic pile to split water into hydrogen and oxygen at metal wires, the first electrolysis of water.",
        details = "Nicholson and Carlisle reported the decomposition of water in London in 1800, in the same year as Volta's letter on the pile. Their experiment began the electrochemistry that Davy and others extended, and it is the practice behind the electrolytic telegraphs of 1804 and 1809.",
    ),
)
