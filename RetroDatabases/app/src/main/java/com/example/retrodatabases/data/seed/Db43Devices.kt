package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1610 to 1620: Galileo's and Kepler's telescopes, Napier's rods, a weighing chair, water machines, a microscope, an air thermometer and a dark-glass telescope for the Sun. */
val db43Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Napier's bones, the rods for multiplication",
        originalTitle = "Rabdologiae, seu numerationis per virgulas libri duo",
        author = "John Napier",
        year = 1617,
        summary = "Napier's rods were strips marked with the multiples of each digit, so that a multiplication was done by reading off the rods and adding the figures in columns.",
        details = "Napier described the rods in his Rabdologiae of 1617, printed at Edinburgh, and the set could be used to multiply and divide without tables of products. The rods were made in wood, bone or card, and the method is the lattice of the schoolbook, with the carrying done by hand.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Galileo's telescope with a concave eyepiece",
        originalTitle = "perspicillum",
        author = "Galileo Galilei",
        year = 1610,
        summary = "Galileo's glass combined a convex objective with a concave eyepiece, which gave an upright image with a narrow field, and it was the instrument with which he observed the sky in 1610.",
        details = "Galileo built his instruments in 1609 and improved them to a magnification of about twenty times by the end of that year, and he called the glass a perspicillum. The concave eyepiece kept the image upright but narrowed the field, which limited what a single observation could show.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Kepler's two-lens telescope from the Dioptrice",
        originalTitle = "Dioptrice",
        author = "Johannes Kepler",
        year = 1611,
        summary = "Kepler described a telescope with two convex lenses, which gave a wider field than Galileo's glass but an image turned upside down.",
        details = "Kepler set out the design in his Dioptrice, printed at Augsburg in 1611, and its inverted image was accepted by astronomers because they used it to measure positions in the sky. Later makers of large telescopes adopted the same two-lens form.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Hydraulics",
        title = "De Caus's water-driven machines",
        originalTitle = "Les raisons des forces mouvantes",
        author = "Salomon de Caus",
        year = 1615,
        summary = "De Caus's book of 1615 described pumps, fountains and moving machines driven by water and air, many of them designed for the gardens of the Elector Palatine at Heidelberg.",
        details = "The book was printed at Frankfurt in 1615 and was illustrated with the machines themselves, so that readers could build them. De Caus had worked in Heidelberg, where water-powered figures and organs were made for the garden, and the designs show the use of pumps and levers to move the parts.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Medicine",
        title = "Santorio's weighing chair for insensible perspiration",
        originalTitle = "De statica medicina",
        author = "Santorio Santorio",
        year = 1614,
        summary = "Santorio's weighing chair hung from a steelyard so that a person could be weighed before and after eating and sleeping, to measure what the body gave off without being seen.",
        details = "Santorio described the chair and his weighings in his De statica medicina of 1614, and he recorded the difference between the food taken in and the weight lost as insensible perspiration. The figures are the author's own, and their exactness is a matter of later dispute.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Drebbel's compound microscope",
        originalTitle = "Drebbel's compound microscope",
        author = "Cornelis Drebbel",
        year = 1619,
        summary = "Drebbel is usually credited with one of the first compound microscopes, which used two lenses to magnify small objects, though the first such instrument is claimed for several makers.",
        details = "Some accounts credit Drebbel with an early compound microscope, dated by the sources only approximately to around 1619, and accounts differ on whether he or the spectacle makers of the Netherlands made the first one. Galileo's compound instrument of the 1620s is also cited, so the origin and the date of the device are both disputed.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Measuring heat",
        title = "Fludd's thermoscope, illustrated in 1617",
        originalTitle = "Utriusque cosmi maioris scilicet et minoris metaphysica, physica atque technica historia",
        author = "Robert Fludd",
        year = 1617,
        summary = "Fludd's book of 1617 illustrated an air thermoscope, a glass tube with its open end in water whose level moved as the warmth of the air changed.",
        details = "The illustration appears in Fludd's history of the greater and lesser worlds, printed at Oppenheim in 1617, and the instrument is one of the earliest drawn thermoscopes. Accounts differ on priority, since Galileo's thermoscope of the 1590s is also cited as an early air thermometer.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Scheiner's telescope with a dark glass for the Sun",
        originalTitle = "Tres epistolae de maculis solaribus",
        author = "Christoph Scheiner",
        year = 1611,
        summary = "Scheiner watched the Sun through a telescope fitted with a dark glass, the arrangement he used for the sunspot observations that he began in 1611.",
        details = "His three letters on the spots were printed in 1612, and they describe the observations made this way. Johannes Fabricius published his own sunspot observations in 1611, and accounts differ on who first saw the spots and who first used a dark glass on the Sun.",
    ),

)
