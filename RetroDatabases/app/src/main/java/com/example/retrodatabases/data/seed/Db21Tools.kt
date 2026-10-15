package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1830 to 1840: an optical viewer, a table of indices, magnetic maps, a galvanometer, a pendulum and a magnetometer. */
val db21Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Wheatstone's reflecting stereoscope",
        originalTitle = "On some remarkable, and hitherto unobserved, phenomena of binocular vision (1838)",
        author = "Charles Wheatstone",
        year = 1838,
        summary = "A viewing instrument with mirrors that shows each eye a separate picture, so two drawings of one object fuse into a single scene with depth.",
        details = "Wheatstone described the instrument and the effect in his 1838 paper on binocular vision, which was read to the Royal Society. His first pictures were drawings, since the photographs of the next year were not yet available. The record uses the 1838 paper date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.TOOL,
        section = "Index tables",
        title = "Jacobi's Canon Arithmeticus",
        originalTitle = "Canon arithmeticus (1839)",
        author = "Carl Gustav Jacob Jacobi",
        year = 1839,
        summary = "A table of indices for each prime and prime power below 1000, giving for every number its power of a chosen primitive root, so that multiplication becomes addition.",
        details = "The tables were printed in Berlin in 1839 with a Latin introduction, and they run in both directions, from numbers to indices and from indices to numbers. Jacobi chose generators such as 10 or minus 10 wherever he could. The 1956 edition was recomputed by Wilhelm Patz with different primitive roots.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.TOOL,
        section = "Geomagnetism",
        title = "Gauss and Weber's Atlas of terrestrial magnetism",
        originalTitle = "Atlas des Erdmagnetismus nach den Elementen der Theorie entworfen (1840)",
        author = "Carl Friedrich Gauss and Wilhelm Weber",
        year = 1840,
        summary = "A set of maps of the earth's magnetic field, drawn from the theory of the field and from the readings of the magnetic union.",
        details = "The atlas appeared in 1840, when the observations of the magnetic union had been gathered, and it set out the magnetic elements of the earth as maps. Each map shows one element over the whole globe, so that the field could be compared across regions. The record uses the 1840 date.",
        rare = true,
    ),

    entry(
        databaseId = 21,
        kind = Kind.TOOL,
        section = "Electrical measurement",
        title = "Pouillet's tangent galvanometer",
        originalTitle = "Paper on the measurement of electric currents (1837)",
        author = "Claude Servais Mathias Pouillet",
        year = 1837,
        summary = "A galvanometer that reads a current from the angle of a magnetic needle, with the current proportional to the tangent of that angle.",
        details = "Pouillet described the tangent galvanometer in 1837. The reading depends on the coil's constant, which has to be known before the instrument gives an absolute current. The instrument became a standard tool for measuring currents in the laboratory. The record uses the 1837 date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.TOOL,
        section = "Gravity",
        title = "Bessel's reversible pendulum",
        originalTitle = "Untersuchungen uber die Lange des einfachen Secundenpendels (1837)",
        author = "Friedrich Wilhelm Bessel",
        year = 1837,
        summary = "A pendulum swung about two knife edges in turn, so that its effective length could be found from the period and used to measure the length of the seconds pendulum.",
        details = "Bessel's pendulum experiments were made from 1835, and their results were printed in 1837. He corrected the measured length for the air and for the form of the pendulum. The record uses the 1837 publication date.",
        rare = true,
    ),

    entry(
        databaseId = 21,
        kind = Kind.TOOL,
        section = "Magnetism",
        title = "Gauss's magnetometer for absolute field measurement",
        originalTitle = "Intensitas vis magneticae terrestris ad mensuram absolutam revocata (1832)",
        author = "Carl Friedrich Gauss",
        year = 1832,
        summary = "A bar magnet hung on a fibre, used with timing and length to measure the earth's magnetic force in absolute units rather than on an arbitrary scale.",
        details = "Gauss's 1832 paper set out the absolute measurement of the earth's magnetic force, with the units of millimetre, milligram and second. The instrument's readings were converted to those units, so that observers in different places could compare results. The record uses the 1832 paper.",
        rare = true,
    ),
)
