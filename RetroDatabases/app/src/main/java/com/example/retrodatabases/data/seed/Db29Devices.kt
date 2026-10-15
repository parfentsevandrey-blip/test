package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1750 to 1760: a lightning rod, an achromatic lens, a sea clock, a wheel, a lever escapement, an orrery, a telescope and a lighthouse. */
val db29Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "Franklin's lightning rod",
        originalTitle = "Lightning rod",
        author = "Benjamin Franklin",
        year = 1752,
        summary = "A pointed iron rod set above a building and joined to the ground, which carries a lightning strike away harmlessly.",
        details = "Franklin proposed the rod after his kite experiment of 1752, and rods were fixed to buildings in Philadelphia soon after. Accounts differ on which came first, Franklin's kite or d'Alibard's iron rod at Marly in France.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Dollond's achromatic lens",
        originalTitle = "Achromatic object glass",
        author = "John Dollond",
        year = 1758,
        summary = "A telescope lens made of two kinds of glass, crown and flint, so that the colours come to the same focus.",
        details = "Dollond took out a patent in 1758 for a lens of crown and flint glass, which cut the coloured fringes that a single lens gave. The design made the refracting telescope far more useful for astronomy and surveying.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Harrison's H3 marine timekeeper",
        originalTitle = "Third marine timekeeper",
        author = "John Harrison",
        year = 1757,
        summary = "A sea clock that John Harrison built after his H2, in an effort to find longitude at sea, completed around 1757.",
        details = "Harrison used the H3 to test ideas that led to his later H4. It was one of a series of marine timekeepers that Harrison built in the 1750s.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Hydraulics",
        title = "Smeaton's overshot waterwheel trials",
        originalTitle = "An experimental enquiry concerning the natural powers of water and wind to turn mills",
        author = "John Smeaton",
        year = 1759,
        summary = "Smeaton's trials of model wheels found that the overshot wheel, fed from above, did more work than the undershot wheel.",
        details = "Smeaton published the trials in 1759. Several accounts give the ratio of the overshot wheel's work to the undershot wheel's as about two to one, and the exact ratio is disputed.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Clockwork",
        title = "Mudge's detached lever escapement",
        originalTitle = "Detached lever escapement",
        author = "Thomas Mudge",
        year = 1755,
        summary = "A watch escapement in which a lever releases the balance from the train, so that the balance runs with less interference.",
        details = "Mudge devised the lever escapement in the 1750s, and it is usually dated to about 1755. Accounts range from 1754 to 1756, and some give an early trial in a bracket clock around 1750.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Planetaria",
        title = "Ferguson's all-wood orrery",
        originalTitle = "Orrery",
        author = "James Ferguson",
        year = 1755,
        summary = "A model of the Sun, Earth and planets built entirely of wood, which shows the planets moving around the Sun at their relative speeds.",
        details = "The Science Museum Group dates the orrery to 1755 to 1756. Its gears set the planets at their relative periods, so the motion can be watched on the model.",
        rare = true,
    ),

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "James Short's reflecting telescope",
        originalTitle = "Reflecting telescope",
        author = "James Short",
        year = 1755,
        summary = "A reflecting telescope with a speculum-metal mirror of the kind James Short made in quantity during the 1750s.",
        details = "Short made reflecting telescopes of speculum metal in the 1750s, and his instruments were bought by astronomers and collectors. The record gives 1755 as a working date for the decade, because his early instruments are not dated exactly.",
        rare = true,
    ),

    entry(
        databaseId = 29,
        kind = Kind.DEVICE,
        section = "Lighthouses",
        title = "Smeaton's third Eddystone lighthouse",
        originalTitle = "Eddystone Lighthouse",
        author = "John Smeaton",
        year = 1759,
        summary = "A lighthouse on the Eddystone reef, built by John Smeaton of dovetailed stone and first lit in 1759.",
        details = "Smeaton built the third Eddystone light on the reef off Plymouth, and it was lit in 1759. He shaped the stones to interlock and used a hydraulic lime, which let the tower stand on the rock for more than a century.",
    ),

)
