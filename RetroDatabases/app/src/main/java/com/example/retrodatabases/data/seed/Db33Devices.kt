package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1710 to 1720: a steam engine, a tuning fork, an early piano, a thermometer, a typewriter patent, a diving bell, an escapement and a violin. */
val db33Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Power",
        title = "Newcomen's atmospheric steam engine",
        originalTitle = "Newcomen engine",
        author = "Thomas Newcomen",
        year = 1712,
        summary = "A piston that the air pressure drives down after the steam under it has been condensed, which pumped water out of mines.",
        details = "The engine set up at Dudley Castle in 1712 pumped water from a coal mine. Its cylinder was filled with steam, which a jet of cold water condensed, so the air pressure pushed the piston down and the beam raised the pump rod at the other end.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Music",
        title = "Shore's tuning fork",
        originalTitle = "Tuning fork",
        author = "John Shore",
        year = 1711,
        summary = "A steel fork that sounds one fixed pitch when struck, which gave musicians a reference to tune their instruments by.",
        details = "John Shore, a trumpeter in London, is credited with the fork of 1711. Musicians used it to tune organs, harpsichords and voices, but pitch standards varied for more than a century, so each fork gave only a local reference.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Keyboards",
        title = "Cristofori's hammer piano as Maffei described it",
        originalTitle = "Gravicembalo col piano e forte",
        author = "Bartolomeo Cristofori, described by Scipione Maffei",
        year = 1711,
        summary = "A keyboard whose hammers strike the strings and give a soft or loud touch, described in print in 1711 by an Italian writer.",
        details = "Cristofori built the instrument in Florence around 1700, and Scipione Maffei published the first description in the Giornale de' letterati d'Italia in 1711. Maffei's account explains the hammer action and the soft and loud touch, which is the principle of the modern piano.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Thermometry",
        title = "Fahrenheit's mercury thermometer",
        originalTitle = "Thermometer",
        author = "Daniel Gabriel Fahrenheit",
        year = 1714,
        summary = "A thermometer with mercury in the glass, which gave steadier readings than the alcohol instruments that came before it.",
        details = "Fahrenheit made mercury thermometers from 1714, and he graded them on a scale published in 1724. That scale fixed 0 at a brine mixture, 32 at freezing water, 96 at body heat and 212 at boiling water.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Writing",
        title = "Mill's patent for transcribing letters",
        originalTitle = "Machine for Transcribing Letters",
        author = "Henry Mill",
        year = 1714,
        summary = "A patent for an artificial machine or method of impressing letters, often treated as the first typewriter proposal.",
        details = "The patent, numbered 395, is dated 7 January 1714 and describes an artificial machine or method for impressing letters one after another. No machine survives, and the patent does not explain how the device worked, so accounts differ on how far it describes a typewriter.",
        rare = true,
    ),

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Diving",
        title = "Halley's diving bell with air barrels",
        originalTitle = "Diving bell with air barrels",
        author = "Edmond Halley",
        year = 1716,
        summary = "A bell lowered from a boat with barrels of air sent down to refill it, which let divers stay under water for longer.",
        details = "Halley described his bell and its air supply in 1716, and he tested it by descending from a boat. The bell's air shrinks as the water pressure rises, so the volume of air inside falls with depth as Boyle's law predicts.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Graham's dead-beat escapement",
        originalTitle = "Dead-beat escapement",
        author = "George Graham",
        year = 1715,
        summary = "A clock escapement that holds the wheel train still between impulses, which let regulator clocks keep steadier time.",
        details = "Graham is usually credited with the dead-beat escapement, which this record dates to about 1715, and the year is approximate. The design stopped the recoil of the escape wheel at each swing, so the pendulum was disturbed far less by the train.",
        rare = true,
    ),

    entry(
        databaseId = 33,
        kind = Kind.DEVICE,
        section = "Violins",
        title = "Stradivari's Messiah violin",
        originalTitle = "Messiah",
        author = "Antonio Stradivari",
        year = 1716,
        summary = "A violin made by Antonio Stradivari in 1716 that survives in unusually good condition.",
        details = "The label of this instrument gives the Cremona maker and the year 1716. It is now kept as a museum piece and is seldom played, which is why it shows so clearly how a Stradivari looked when new.",
    ),
)
