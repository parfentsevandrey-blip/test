package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1820 to 1830: the first electromagnetic instruments and motors, a calculating engine and a lighthouse lens. */
val db22Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Electromagnetism",
        title = "Schweigger's multiplier of 1820",
        originalTitle = "Multiplicator (1820)",
        author = "Johann Schweigger",
        year = 1820,
        summary = "A coil of many turns of wire around a compass needle, which multiplied the small deflection that a current gave to the needle.",
        details = "Schweigger showed the multiplier at Halle in September and again in November 1820, within months of Oersted's discovery. Two others built similar multipliers in 1821, and the simple instruments that followed became known as galvanometers.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Electromagnetism",
        title = "Faraday's electric rotation device",
        originalTitle = "Electro-magnetic rotations (1821)",
        author = "Michael Faraday",
        year = 1821,
        summary = "A wire carrying current circled around a magnet, and a magnet circled around a wire, the first continuous motion that electricity produced in a laboratory.",
        details = "Faraday made the two rotations in September 1821, and they showed that a current and a magnet exert forces that turn one about the other. The apparatus is the principle of the electric motor, though it could do no useful work.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Calculating engines",
        title = "Babbage's Difference Engine No. 1",
        originalTitle = "Note on the application of machinery to the computation of astronomical and mathematical tables (1822)",
        author = "Charles Babbage",
        year = 1822,
        summary = "Babbage's first calculating engine, planned from 1822, worked out polynomial tables by the method of differences, using columns of toothed wheels.",
        details = "Babbage read his note on machinery for tables to the Royal Astronomical Society in 1822 and won government funding from 1823. A demonstration piece of the engine was shown in 1832, and the full machine was never completed.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Electromagnetism",
        title = "Barlow's wheel, an early electric motor",
        originalTitle = "Barlow's wheel (1822)",
        author = "Peter Barlow",
        year = 1822,
        summary = "A toothed copper wheel, with its rim dipping into mercury cups between magnet poles, turned steadily while a current ran through it.",
        details = "Barlow described the wheel in 1822 as a demonstration of continuous rotation driven by electromagnetism. Its turning force was small, so it was an exhibit of the principle rather than a machine.",
        rare = true,
    ),

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Nicol's prism of 1828",
        originalTitle = "Nicol prism (1828)",
        author = "William Nicol",
        year = 1828,
        summary = "A calcite crystal cut on a diagonal and cemented together again, so that only one of the two polarised rays passes through it.",
        details = "Nicol described the prism in 1828, and it served as the usual polariser in optical work for decades. The cement, Canada balsam, lets the ordinary ray be reflected out of the beam while the other passes through.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Lighthouses",
        title = "Fresnel's lighthouse lens",
        originalTitle = "Lentille a echelons, the stepped lens (1822)",
        author = "Augustin Fresnel",
        year = 1822,
        summary = "A stepped glass lens of concentric rings that bent the light of a lamp into a narrow beam, far lighter than a solid lens of the same power.",
        details = "Fresnel described the stepped design in 1822, and the first French lighthouse to use it was lit in 1823 at Cordouan. Each ring bends light like a slice of a thick lens, so the whole lens can be made thin.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Electromagnetism",
        title = "Arago's rotating copper disc",
        originalTitle = "Arago's magnetic rotations (1824)",
        author = "Francois Arago",
        year = 1824,
        summary = "A copper disc set spinning under a magnetic needle dragged the needle round with it, although no contact joined the disc to the needle.",
        details = "Arago reported the effect in 1824. It was explained in the 1830s by currents that the magnet induced in the moving copper.",
    ),

    entry(
        databaseId = 22,
        kind = Kind.DEVICE,
        section = "Electromagnetism",
        title = "Sturgeon's electromagnet of 1825",
        originalTitle = "Electromagnet, a horseshoe of iron wound with copper wire (1825)",
        author = "William Sturgeon",
        year = 1825,
        summary = "A horseshoe of iron wound with copper wire that became a strong magnet while a current flowed and lost its pull when the current was cut.",
        details = "Sturgeon described the magnet in 1825 in a paper to the Society of Arts, and it lifted many times its own weight. Accounts differ on the figures for its first load and winding, so this record gives no numbers.",
    ),
)
