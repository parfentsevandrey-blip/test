package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1720 to 1730: punched paper and cards for looms, two compensated pendulums, a fire pump, a harp, a grinder and a colour instrument. */
val db32Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Looms",
        title = "Bouchon's punched paper for a drawloom",
        originalTitle = "Bouchon's perforated paper",
        author = "Basile Bouchon",
        year = 1725,
        summary = "A strip of paper with holes in it that set which warp threads were lifted, so that a loom could repeat a pattern from the strip.",
        details = "Bouchon's device of 1725 used a paper strip pierced at set positions to choose the threads that were lifted for each row of the pattern. Accounts differ on how much of the method was his own and how much came from earlier drawloom practice.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Looms",
        title = "Falcon's punched-card loom",
        originalTitle = "Falcon's punched-card loom",
        author = "Jean-Baptiste Falcon",
        year = 1728,
        summary = "A drawloom that used punched cards tied together in a chain, in place of a paper strip, to set the warp threads for each pick.",
        details = "Falcon's card chain of 1728 made a pattern easier to change than a long paper strip, and it was one of the steps toward card-controlled looms. The loom is usually compared with Bouchon's paper system of 1725.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Graham's mercury pendulum",
        originalTitle = "Graham's mercurial compensation",
        author = "George Graham",
        year = 1722,
        summary = "A clock pendulum with a jar of mercury at its bottom, which rises as the temperature rises and offsets the lengthening of the steel rod.",
        details = "Graham reported the compensation in 1722, and some accounts give 1721 for the first trial. The steel rod lengthens with heat while the mercury in the jar expands and rises, so the centre of the bob stays at a fixed distance from the pivot.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Harrison's gridiron pendulum",
        originalTitle = "Gridiron pendulum",
        author = "John Harrison",
        year = 1726,
        summary = "A pendulum built from alternate rods of steel and brass, arranged so that their different expansions cancel and the length stays fixed.",
        details = "Harrison's gridiron, dated here to 1726, is a temperature-compensated pendulum, and Harrison later applied the same care to his other clock work. The year given is the one usually attached to the first working of the design.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Fire engines",
        title = "Newsham's patented fire pump",
        originalTitle = "Newsham's fire engine",
        author = "Richard Newsham",
        year = 1721,
        summary = "A hand-operated fire pump that threw water from a tank, covered by a patent of 1721.",
        details = "Fire pumps of this kind were worked by hand, and Newsham's patent is recorded as the earliest for a hand-operated fire pump. The design is one step in the history of fire fighting machinery that followed in the next decades.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Harps",
        title = "Hochbrucker's five-pedal harp",
        originalTitle = "Single-action harp with five pedals",
        author = "Jacob Hochbrucker",
        year = 1720,
        summary = "A harp with five pedals, each of which raised the chosen strings by a semitone, so that the player could change key without retuning.",
        details = "Hochbrucker made this harp in Bavaria, dated here to about 1720. Its pedals changed the pitch of chosen strings by a semitone, the same step as a half tone on a keyboard.",
        rare = true,
    ),

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Food machines",
        title = "Churchman's cocoa-bean grinder",
        originalTitle = "Cocoa grinder patent",
        author = "Walter Churchman",
        year = 1729,
        summary = "A mechanical grinder for cocoa beans, patented in Bristol in 1729 and described in the sources as the first of its kind.",
        details = "Churchman's patent of 1729 covered a machine to grind cocoa beans, which had until then been ground by hand. It is one of the early patents for machines that replaced hand work in food preparation.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Castel's ocular harpsichord",
        originalTitle = "Clavecin oculaire",
        author = "Louis-Bertrand Castel",
        year = 1725,
        summary = "An idea for an instrument that linked colours to musical notes, so that a listener could see the music as well as hear it.",
        details = "Castel described the instrument in 1725, but no drawings of it survive, so its design is known only from descriptions. The sources used here do not settle whether a working instrument was ever built.",
        rare = true,
    ),

)
