package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1740 to 1750: a silk loom with a drum, a glass jar that stored charge, instruments of heat, air and gunnery. */
val db30Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Weaving",
        title = "Vaucanson's silk loom with a perforated drum",
        originalTitle = "Metier a tisser la soie",
        author = "Jacques de Vaucanson",
        year = 1745,
        summary = "A silk loom whose pattern was set by a perforated drum, with hooks that lifted the threads the design called for.",
        details = "Vaucanson's loom was shown in the 1740s, and accounts differ on whether its pattern came from a perforated drum or from punched cards, with dates that vary between 1740 and 1745. The drum version was costly and fixed to one repeating design.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "The Leyden jar",
        originalTitle = "Electrical jar of Kleist and Musschenbroek",
        author = "Ewald Georg von Kleist and Pieter van Musschenbroek",
        year = 1745,
        summary = "A glass jar coated with metal inside and out, which stored electric charge so that a spark or shock could be drawn from it later.",
        details = "Kleist made the jar in 1745, and Musschenbroek at Leiden described his version in early 1746. Accounts differ on the day of Kleist's first success, given as 11 October 1745 in some histories and 4 November 1745 in others, and the jar became the model for early capacitors.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Gunnery",
        title = "Robins's ballistic pendulum",
        originalTitle = "New Principles of Gunnery",
        author = "Benjamin Robins",
        year = 1742,
        summary = "A heavy block hung on a pivot, which a musket ball struck so that the angle of its swing gave the speed of the ball.",
        details = "Robins described the pendulum in his book of 1742 and used it to measure the muzzle speed of musket balls. The speed follows from momentum and energy, since the block's rise through the swing angle shows how fast the ball was moving when it struck.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Ventilation",
        title = "Hales's ventilators for ships and prisons",
        originalTitle = "A Description of Ventilators",
        author = "Stephen Hales",
        year = 1743,
        summary = "A hand-worked box for changing the air in the holds of ships and in crowded rooms such as prisons.",
        details = "Hales described his ventilators in a book of 1743, which set out how foul air could be drawn out of enclosed spaces. The design was meant for ships, prisons and hospitals, where stale air was thought to cause sickness.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Thermometry",
        title = "Celsius's centigrade thermometer",
        originalTitle = "Observations of two persistent degrees on a thermometer",
        author = "Anders Celsius",
        year = 1742,
        summary = "A thermometer scale with 100 degrees between the freezing and boiling points of water, first written with 0 at the boiling point.",
        details = "Celsius published the scale in 1742, with the boiling point at 0 and the freezing point at 100. Accounts differ on who first reversed it to the form used today, and the names given are Stromer and Linnaeus.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Solar heat",
        title = "Buffon's burning mirrors",
        originalTitle = "Miroirs ardents",
        author = "Georges-Louis Leclerc, comte de Buffon",
        year = 1747,
        summary = "A set of many small plane mirrors that focused sunlight on one spot, so that the light could set wood alight at a distance.",
        details = "Buffon tested the mirrors in Paris in 1747 in summer sunlight, and his reports described how the focus grew with the number of mirrors. Accounts differ on how many mirrors he used and on how far the flames carried.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Heating",
        title = "Franklin's Pennsylvania fireplace",
        originalTitle = "An Account of the New Invented Pennsylvanian Fire-Places",
        author = "Benjamin Franklin",
        year = 1744,
        summary = "An iron insert for a fireplace, which Franklin said gave more heat from the same wood and sent less smoke into the room.",
        details = "Franklin described the stove in a pamphlet of 1744, and he dated its invention to about 1742. Accounts differ on the date of the first installation, and the design was copied in America and Europe.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.DEVICE,
        section = "Spinning",
        title = "Bourn's roller spinning machine",
        originalTitle = "Patent of 1748 for spinning by rollers",
        author = "Daniel Bourn",
        year = 1748,
        summary = "A spinning machine in which pairs of rollers drew out the fibres before the spindle twisted them into thread.",
        details = "Bourn took out his patent in 1748. Accounts differ on how far his machine was his own and how far it followed Lewis Paul's earlier roller patent of 1738.",
        rare = true,
    ),
)
