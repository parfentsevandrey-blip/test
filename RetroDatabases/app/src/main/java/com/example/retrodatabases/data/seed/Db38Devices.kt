package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1660 to 1670: telescopes and a micrometer, an electrical globe, weather instruments, a clock and a calculating machine. */
val db38Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Gregory's reflecting telescope with a concave mirror",
        originalTitle = "Optica promota",
        author = "James Gregory",
        year = 1663,
        summary = "A reflecting telescope design in which a small concave mirror sends the image back through a hole in the main mirror, giving an upright view.",
        details = "Gregory described the design in his Optica promota of 1663, and it is now called the Gregorian telescope. Its concave second mirror gives an upright image, which sets it apart from the flat second mirror of the design Newton used later.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "Guericke's sulphur ball, an early static generator",
        originalTitle = "Experimenta nova (ut vocantur) Magdeburgica de vacuo spatio",
        author = "Otto von Guericke",
        year = 1663,
        summary = "A large ball turned by hand and rubbed to build up a static charge that drew light objects to it, one of the first machines for producing electricity.",
        details = "The machine is usually dated to about 1663, but Guericke first described it in print in his Experimenta nova of 1672. Accounts differ on the date of its construction and on whether the ball was made of sulphur or of glass, and histories often call it the first electrostatic generator.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Weather",
        title = "Hooke's wheel barometer",
        originalTitle = "Hooke's wheel barometer",
        author = "Robert Hooke",
        year = 1663,
        summary = "A barometer in which a float on the mercury turns a wheel and a pointer across a dial, so that the air pressure reads off a scale.",
        details = "The Royal Society's drawing of the instrument is dated 7 October 1663, the day Hooke's paper on weather observations came before the Society, and Robert Boyle is credited with coining the name. The instrument was illustrated in Micrographia in 1665 and described in the Philosophical Transactions in June 1666.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Weather",
        title = "Wren's weather clock",
        originalTitle = "Weather clock",
        author = "Christopher Wren",
        year = 1663,
        summary = "A clock with attached instruments that recorded the weather, including a side that registered the wind, which Wren devised in 1663.",
        details = "In October 1663 the Royal Society ordered Hooke to make thermometers for the clock, so the instruments were meant to work together. The surviving descriptions are short, so only the outline of its working is clear.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Morland's calculating machine",
        originalTitle = "Morland's calculating machine",
        author = "Samuel Morland",
        year = 1666,
        summary = "An adding machine built for sums of pounds, shillings and pence, and dated 1666 on the surviving examples.",
        details = "The Science Museum's examples carry an engraved inscription naming Morland as inventor and the year 1666, and production is dated from 1666 to 1695. The design recalls Pascal's machines of the 1640s, and it was made for money sums rather than decimal ones.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Auzout's telescope micrometer",
        originalTitle = "Micrometre",
        author = "Adrien Auzout",
        year = 1666,
        summary = "A micrometer fitted to a telescope that measured the small angular size of a star or planet inside the eyepiece.",
        details = "Auzout reported the instrument to the Royal Society in 1666, and the Society's catalogue dates the printed account to January 1667, while an older biography gives 1666. Towneley argued that William Gascoigne had devised a similar instrument before the Civil War, a priority claim that the surviving papers do not settle.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Weather",
        title = "Hooke's wind gauge",
        originalTitle = "Wind gauge",
        author = "Robert Hooke",
        year = 1667,
        summary = "A gauge that measured the pressure of the wind against a plate, made to help seamen judge how hard the wind was blowing.",
        details = "Sprat's History of the Royal Society, printed in 1667, shows the wind gauge beside Hooke's wheel barometer and hygrometer, so 1667 is a date of print rather than a firm date of invention. The histories do not settle the dates of Hooke's weather instruments, and the invention date of this gauge remains unclear.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Newton's reflecting telescope",
        originalTitle = "Newton's reflecting telescope",
        author = "Isaac Newton",
        year = 1668,
        summary = "A small reflecting telescope that Newton built in 1668, with a concave metal mirror in place of a lens to gather the light.",
        details = "Newton built the instrument in 1668 and showed it to the Royal Society that December. A mirror in place of a lens avoided the colour fringes that troubled refracting telescopes.",
    ),

)
