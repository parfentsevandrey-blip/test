package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1640 to 1650: a telescope micrometer, a pendulum design, a calculating machine, a barometer, a toy diver, a vacuum siphon, a flying chariot on paper and a hydrostatic press. */
val db40Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Gascoigne's micrometer for the telescope",
        originalTitle = "Micrometer for a telescope",
        author = "William Gascoigne",
        year = 1640,
        summary = "Two fine wires moved by a screw, placed in front of a telescope's eyepiece, let an astronomer measure the angle between two stars.",
        details = "The prototype is dated to about 1640 on the strength of a later account by Richard Towneley, and Gascoigne was killed at Marston Moor on 2 July 1644 before he could publish. A 1911 encyclopedia argues that his work came about twenty years before similar efforts by Huygens, Auzout and Picard.",
        rare = true,
    ),

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Timekeeping",
        title = "Galileo's pendulum clock design, drawn by his son",
        originalTitle = "Disegno del pendolo per l'orologio",
        author = "Galileo Galilei",
        year = 1641,
        summary = "Galileo, blind by then, described a pendulum mechanism that would drive a clock's escapement, and the design was drawn from his explanations.",
        details = "The idea is dated to 1641 at Arcetri, and Galileo explained it in words to his son Vincenzio, who sketched it, although accounts differ on who made the surviving drawing and some credit Viviani instead. Viviani's letter of 20 August 1659 to Leopoldo de' Medici enclosed a drawing, and building the prototype began only in April 1649 and was not finished in the lifetime of either man.",
        rare = true,
    ),

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Calculating machines",
        title = "Pascal's arithmetic machine, the Pascaline",
        originalTitle = "La Pascaline",
        author = "Blaise Pascal",
        year = 1642,
        summary = "A box of geared wheels that added and subtracted sums of money, built by Pascal to help his father with tax accounts at Rouen.",
        details = "Pascal began the machine in 1642 and built many models over the following years, and he sought a royal privilege to sell it in the late 1640s. The wheels carried tens automatically, and subtraction was done by adding complements, so the operator entered digits and turned the wheels.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Pressure",
        title = "Torricelli's mercury barometer",
        originalTitle = "Torricelli's mercury barometer",
        author = "Evangelista Torricelli",
        year = 1643,
        summary = "A glass tube full of mercury, closed at one end and turned upright in a dish, stood about 76 centimetres high at sea level, and its height changed with the weight of the air.",
        details = "Torricelli made the tube experiment in Florence in 1643 and described it in a letter of 1644 to Michelangelo Ricci. The column was held up by the pressure of the air on the dish, which explained why its height changed with the weather and with altitude.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Physical toys",
        title = "The Cartesian diver, Magiotti's glass toy",
        originalTitle = "Cartesian diver",
        author = "Raffaello Magiotti",
        year = 1648,
        summary = "A small hollow glass figure with an air bubble floats in a water-filled bottle and sinks when the bottle is squeezed, showing that air shrinks under pressure.",
        details = "Accounts credit Magiotti with the first version of the toy in 1648, although its name honours Descartes. The toy is a simple demonstration of buoyancy and of the compression of air, and it became a common item in the study of pressure.",
        rare = true,
    ),

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Vacuum",
        title = "Pascal's siphon and tube for the vacuum experiments",
        originalTitle = "Experiences nouvelles touchant le vuide",
        author = "Blaise Pascal",
        year = 1647,
        summary = "A glass siphon and tube, filled with liquid, that Pascal used in 1647 to test what happens above a liquid when no air can enter.",
        details = "Pascal printed the new experiments in Paris in 1647, and they joined the dispute over whether nature abhors a vacuum, a dispute that Torricelli's mercury tube of 1643 had sharpened. The apparatus was simple, and its results were argued over in print for years afterwards.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Mechanical design",
        title = "Wilkins's flying chariot in Mathematicall Magick",
        originalTitle = "Mathematicall Magick, or the Wonders That May Be Performed by Mechanicall Geometry",
        author = "John Wilkins",
        year = 1648,
        summary = "A design for a chariot to carry a person through the air, set out in Wilkins's book on mechanical wonders, which was never built.",
        details = "Wilkins printed the book in London in 1648, and it gathers mechanical designs for wonders that a skilled craftsman could make, including the flying chariot. The chariot was a proposal in print and was never built, so it is a device that exists only on paper.",
    ),

    entry(
        databaseId = 40,
        kind = Kind.DEVICE,
        section = "Hydrostatics",
        title = "Pascal's hydrostatic press principle",
        originalTitle = "Traite de l'equilibre des liqueurs",
        author = "Blaise Pascal",
        year = 1650,
        summary = "A closed vessel full of liquid passes a pressure applied at one piston to every other part of its walls, so a small force can lift a large weight.",
        details = "Pascal's principle is set out in his treatise on the equilibrium of liquids, which sources date to between 1651 and 1654 and which was printed only in 1663. The year 1650 is the nearest date in this decade to the writing, and the first printed form is from 1663.",
    ),

)
