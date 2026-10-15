package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1720 to 1730: an astronomical discovery, a study of electrical conduction, a falling-ball test, a weather record and an inoculation in London. */
val db32Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Bradley's discovery of starlight aberration",
        originalTitle = "An Account of a New Discovered Motion of the Fixed Stars",
        author = "James Bradley",
        year = 1729,
        summary = "Stars appear to shift in a small yearly circle because starlight takes time to arrive while the Earth moves, an effect Bradley explained in 1728 and announced in 1729.",
        details = "Bradley watched the shift from 1725 to 1728 while looking for stellar parallax, and he read his account to Halley in January 1729. Accounts differ on how early he understood the cause, and the story of a vane on a Thames boat is a later anecdote.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.PRACTICE,
        section = "Electricity",
        title = "Gray's experiments on electrical conduction",
        originalTitle = "Experiments on electrical conduction",
        author = "Stephen Gray",
        year = 1729,
        summary = "Gray showed that electricity travels along a hemp cord that is held up by silk, so that conductors and insulators could be told apart.",
        details = "Gray's experiments of 1729 hung long cords on silk threads, which stopped the charge from escaping to the support. They separated materials that carry electricity from those that hold it, and the 1729 date is the year of the report.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.PRACTICE,
        section = "Mechanics",
        title = "Gravesande's falling-ball test of speed and depth",
        originalTitle = "Gravesande's falling-ball experiments",
        author = "Willem Jacob 's Gravesande",
        year = 1722,
        summary = "Brass balls dropped into soft clay from different heights made dents whose depth grew with the height, which was taken as proof that the effect of a moving body grows with the square of its speed.",
        details = "Gravesande reported the experiment in 1722 and used it to argue that the effect of a moving body follows the square of its speed, not its speed alone. The depth of each dent was proportional to the height of the drop.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.PRACTICE,
        section = "Weather",
        title = "Celsius's weather record at Uppsala",
        originalTitle = "Uppsala weather observations",
        author = "Anders Celsius",
        year = 1722,
        summary = "A series of daily weather readings kept at Uppsala from 1722, begun with the aim of a run of more than three hundred years.",
        details = "Celsius started the readings at Uppsala in 1722, and the stated plan for them was a run of more than three hundred years. The record is kept here as a plan with its start date, and it is not a claim about how long the readings were actually kept.",
        rare = true,
    ),

    entry(
        databaseId = 32,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Montagu's smallpox inoculation in London",
        originalTitle = "Inoculation against smallpox",
        author = "Lady Mary Wortley Montagu",
        year = 1721,
        summary = "A method of smallpox inoculation learned in Turkey, which Lady Mary Wortley Montagu had used on her own children and introduced to London in 1721.",
        details = "Montagu had the method performed on her son in Constantinople, and in 1721 she had her daughter inoculated in London. Accounts of the trials that followed in London differ in detail, so the record gives only the date of the first London case.",
    ),

)
