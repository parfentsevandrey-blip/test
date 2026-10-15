package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1630 to 1640: a colonial printing press, a horizontal dial, a sliding scale, a hyperbolic lens, a mural quadrant, a burning mirror, a surveying chain and a pair of circular rules. */
val db41Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Stephen Daye's printing press at Cambridge in Massachusetts",
        originalTitle = "The Cambridge press",
        author = "Stephen Daye",
        year = 1638,
        summary = "A printing press was arranged for the colony in 1638 and set up at Cambridge in Massachusetts, the first press in British America, and it produced the Bay Psalm Book in 1640.",
        details = "The venture was arranged by the minister Joseph Glover, who died on the crossing, and his widow carried it on with the locksmith Stephen Daye and his son Matthew. Accounts differ on the start: a freeman's oath broadside is dated late 1638 or January 1639 in different sources, and the first full work is given as March or September 1639, so the year here is the setting up of the press. Some accounts note a press in Mexico City more than a century earlier, so the first label applies only to the British colonies.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Oughtred's horizontal instrument, a dial for problems of the sphere",
        originalTitle = "The Circles of Proportion and the Horizontall Instrument",
        author = "William Oughtred",
        year = 1632,
        summary = "Oughtred's horizontal instrument is a flat dial that works out the problems of spherical triangles which astronomers and navigators met, and it was printed in 1632.",
        details = "The instrument is described in the same book as Oughtred's circles of proportion, and accounts differ on whether that book appeared in 1632 or 1633. Oughtred designed the dial for his own use long before it was made public, so the record dates the device by its first printed form.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Measuring",
        title = "Vernier's sliding scale for reading a fraction of a division",
        originalTitle = "La construction, l'usage, et les proprietez du quadrant nouveau de mathematique",
        author = "Pierre Vernier",
        year = 1631,
        summary = "Vernier's sliding scale, described in 1631, lets a reader take a finer reading than the divisions of a scale by matching a line on the slider with a line on the scale.",
        details = "Vernier described his quadrant in a pamphlet printed at Brussels in 1631, and the principle goes back to the scale that Pedro Nunes described in the 1540s. The name vernier was given to the device later, and the same principle is still used in calipers and sextants.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Descartes's hyperbolic lens for bringing rays to one point",
        originalTitle = "La Dioptrique",
        author = "Rene Descartes",
        year = 1637,
        summary = "Descartes showed in 1637 that a lens with hyperbolic faces brings parallel rays to one point, where a round lens of the same size blurs them.",
        details = "The Dioptrique was printed at Leiden in 1637 as one of three essays that came with the Discours de la methode. Descartes proposed the shape as a design for telescope lenses, but grinding such faces was hard, so round lenses stayed in use for most of the following century.",
        rare = true,
    ),

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The Savilian mural quadrant made by Elias Allen",
        originalTitle = "Savilian mural quadrant",
        author = "Elias Allen",
        year = 1637,
        summary = "The Savilian mural quadrant of 1637 is a wall-mounted quadrant that reads the height of the sun and the stars as they cross the meridian.",
        details = "Elias Allen, a London instrument maker who built several instruments to Oughtred's designs, is credited with the quadrant, which is kept in the History of Science Museum at Oxford. A mural quadrant is fixed to a wall, so its readings depend on the wall being set true to the meridian.",
        rare = true,
    ),

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Cavalieri's burning mirror, the Specchio ustorio",
        originalTitle = "Specchio ustorio",
        author = "Bonaventura Cavalieri",
        year = 1632,
        summary = "Cavalieri's 1632 treatise on burning mirrors used the conic sections to decide which curved mirror gathers the sun's rays into one hot spot.",
        details = "The Specchio ustorio was printed at Bologna in 1632, in the years before Cavalieri published his method of indivisibles. He treated the parabolic mirror as the shape that gathers parallel rays, using the same conic curves that Apollonius had studied.",
        rare = true,
    ),

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "Norwood's surveying chain on the London to York road",
        originalTitle = "Norwood's measuring chain and sextant on the London to York road",
        author = "Richard Norwood",
        year = 1634,
        summary = "Norwood used a measuring chain, and a large sextant for the sun's height, on the road from London to York between 1633 and 1635, correcting the chain's readings for bends and slopes.",
        details = "He began in June 1633 with the sun's height near the Tower of London and finished in June 1635 at York, where he took the sun's height again to find the difference in latitude. Accounts differ on how close the figure he derived for a degree of the meridian came to the modern value.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Oughtred's circles of proportion, a pair of circular rules",
        originalTitle = "The Circles of Proportion and the Horizontall Instrument",
        author = "William Oughtred",
        year = 1632,
        summary = "Oughtred's circles of proportion, printed in 1632, are two circular scales that multiply, divide and find roots when one circle is turned against the other.",
        details = "The circles were described in the same book as Oughtred's horizontal instrument, and accounts differ on whether that book appeared in 1632 or 1633. The circular rule is a different instrument from Delamain's ring of 1630 and from the straight slide rule of 1622.",
        rare = true,
    ),

)
