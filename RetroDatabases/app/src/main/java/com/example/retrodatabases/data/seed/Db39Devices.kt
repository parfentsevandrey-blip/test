package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1650 to 1660: an air pump, a music box of combinations, a meridian line, pendulum clocks, a telescope that found Titan, a sealed thermometer and a quill syringe. */
val db39Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Vacuum",
        title = "Guericke's air pump at Magdeburg",
        originalTitle = "Experimenta nova",
        author = "Otto von Guericke",
        year = 1650,
        summary = "Guericke built a piston pump that drew air out of a closed vessel, one of the first machines made to leave a vacuum on purpose.",
        details = "Guericke was mayor of Magdeburg and built the pump there around 1650, a date that the usual accounts repeat. He described the apparatus later, in the Experimenta nova that was printed in 1672.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Music",
        title = "Kircher's Arca musarithmica, the box for composing church music",
        originalTitle = "Musurgia universalis",
        author = "Athanasius Kircher",
        year = 1650,
        summary = "Kircher's Arca musarithmica was a wooden box of strips and tables that let a person with no training build four-part church music by combining its parts.",
        details = "Kircher described the Arca in his Musurgia universalis of 1650, and he meant it for those who could not compose. Its strips linked pitches and rhythms to modes, and by simple combinations the device was said to yield millions of four-part pieces.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Huygens's pendulum clock",
        originalTitle = "Horologium",
        author = "Christiaan Huygens",
        year = 1656,
        summary = "Huygens's clock was the first to be regulated by a swinging pendulum, which kept its beat far more steadily than the earlier verge clocks.",
        details = "Huygens and his clockmaker Salomon Coster had a working clock by late 1656, and a patent followed in 1657; accounts differ on whether the grant went to Coster alone or to both men. Huygens described the pendulum clock in the Horologium of 1658, the first public account of the design.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "The telescope with which Huygens found Titan",
        originalTitle = "De Saturni Luna Observatio Nova",
        author = "Christiaan Huygens",
        year = 1655,
        summary = "Huygens used a telescope of his own design to see a moon beside Saturn in 1655, the moon now called Titan.",
        details = "He made the sighting on 25 March 1655 and published it in a short Latin note of 1656. The telescope was a long-focus instrument, and it was enough to show a faint companion close to the planet.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Thermometry",
        title = "The Florentine sealed alcohol thermometer",
        originalTitle = "Saggi di naturali esperienze",
        author = "Accademia del Cimento",
        year = 1657,
        summary = "The Florentine thermometer was a glass tube of coloured alcohol sealed at both ends, so that its reading did not depend on the air pressure outside.",
        details = "Accounts date the idea to about 1650 and the academy's use of such tubes to 1657, and the Saggi that described the academy's experiments came out in 1666 or 1667, depending on the bibliography. The scale was set by snow and summer heat, with a hundred divisions between them, and alcohol was chosen over mercury for its quicker response.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Medicine",
        title = "Wren's quill for injecting a dog's vein",
        originalTitle = "Wren's quill for injecting a dog's vein",
        author = "Christopher Wren",
        year = 1656,
        summary = "In March 1656 Wren used a goose quill to inject a liquid into a dog's vein, an early step toward giving medicine straight into the blood.",
        details = "The experiment took place at Oxford, after a talk at Robert Boyle's lodgings, where the dog received an opium solution and recovered fully. Accounts differ on the liquid, with wine and beer also named, and some priority claims rest on later writers who did not see the work.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The first pendulum clocks made in England",
        originalTitle = "The first pendulum clocks made in England",
        author = "Ahasuerus Fromanteel",
        year = 1658,
        summary = "Fromanteel made pendulum clocks in London in 1658, bringing Huygens's new clock to English makers within two years of its invention.",
        details = "Fromanteel, a Dutch-born clockmaker in London, is named as the first English maker of these clocks, which followed the design that Huygens had made known. A rival patent for a similar clock went to Simon Douw in 1658, and Huygens objected to it.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Cassini's meridian line at Bologna",
        originalTitle = "Cassini's meridian line at Bologna",
        author = "Giovanni Domenico Cassini",
        year = 1655,
        summary = "Cassini laid a meridian line across the floor of San Petronio in Bologna, and a hole in the roof let the noon sun mark its position on the line.",
        details = "The line was built to record the sun's noon position over the seasons, which is the measurement it was made for. The year 1655 is the one usually given for the work.",
    ),

)
