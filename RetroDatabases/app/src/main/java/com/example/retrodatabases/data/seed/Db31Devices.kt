package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1730 to 1740: a shuttle, a quadrant, a flow tube, a thermometer, a clock, a spinning machine and two automata. */
val db31Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Textiles",
        title = "Kay's flying shuttle",
        originalTitle = "The flying shuttle",
        author = "John Kay",
        year = 1733,
        summary = "A shuttle thrown along a race by a sharp blow from a picker, so that a weaver could work wider cloth without passing the shuttle by hand.",
        details = "John Kay patented the flying shuttle in 1733. Accounts differ on how much output it added, because the gain depended on the cloth and the loom.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Hadley's reflecting quadrant",
        originalTitle = "Hadley's reflecting quadrant",
        author = "John Hadley",
        year = 1731,
        summary = "A mirror instrument for the angle between two objects, which reads twice the turn of its index arm.",
        details = "Hadley's paper went before the Royal Society on 13 May 1731. Thomas Godfrey in Philadelphia had completed a similar quadrant by 1730, and accounts differ on the priority of the two designs.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Hydraulics",
        title = "Pitot's tube for flow speed",
        originalTitle = "Description de la machine pour mesurer la vitesse des eaux courantes et le sillage des vaisseaux",
        author = "Henri Pitot",
        year = 1732,
        summary = "A bent glass tube with its open end facing the current, in which the rise of water above the river surface gives the speed of flow.",
        details = "Pitot described the machine in a paper for the Paris Academy dated 1732, and he tested it on the Seine. The volume is sometimes dated 1733, so accounts differ on the year, and the record uses 1732.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "Vaucanson's digesting duck",
        originalTitle = "Le canard digerateur",
        author = "Jacques de Vaucanson",
        year = 1739,
        summary = "A mechanical duck that seemed to eat grain, digest it and excrete it, and which drew crowds to its demonstrations in Paris.",
        details = "Vaucanson showed the duck in Paris in 1739, and it moved its wings and neck through gears. The digestion was a sham, since the excreted matter was prepared in advance.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Measurement",
        title = "Reaumur's alcohol thermometer with an 80 degree scale",
        originalTitle = "Regles pour construire les thermometres",
        author = "Rene-Antoine Ferchault de Reaumur",
        year = 1730,
        summary = "A spirit thermometer whose scale runs from 0 at freezing to 80 at boiling water, which gave the scale its name.",
        details = "Reaumur set out the scale and the rules for building the instrument in a paper of 1730. Most countries later replaced his scale with the Celsius and Fahrenheit scales.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Timekeeping",
        title = "Harrison's first marine timekeeper, H1",
        originalTitle = "Harrison's H1 marine timekeeper",
        author = "John Harrison",
        year = 1735,
        summary = "A spring-driven clock built to keep time at sea, the first of Harrison's marine timekeepers, which was tested on a voyage to Lisbon.",
        details = "Harrison completed H1 around 1735, and it was tested on a voyage to Lisbon in 1736. The series of instruments that followed led to his claim for the longitude prize, which was disputed for decades.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Textiles",
        title = "Lewis Paul's roller spinning machine",
        originalTitle = "Machine for spinning by rollers",
        author = "Lewis Paul and John Wyatt",
        year = 1738,
        summary = "A spinning machine in which pairs of rollers drew out the fibre at a set ratio of speeds, an idea that later machines also used.",
        details = "Paul and Wyatt patented the roller spinning machine in 1738, and a mill using it was built at Birmingham in 1741. Accounts differ on how well the machine worked, since the rollers did not hold the fibre evenly.",
        rare = true,
    ),

    entry(
        databaseId = 31,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "Vaucanson's flute player",
        originalTitle = "Le joueur de flute",
        author = "Jacques de Vaucanson",
        year = 1737,
        summary = "A life-size automaton that played a flute by means of bellows, fingers and lips, and which the public saw in Paris.",
        details = "Vaucanson exhibited the flute player in Paris in 1737. Its lips were leather and its breath came from bellows, and it played a repertoire of tunes.",
        rare = true,
    ),
)
