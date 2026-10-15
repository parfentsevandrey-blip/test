package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1880 to 1890: motors, writing and calculating machines, a camera, a scanner and an early radio detector. */
val db16Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Motors",
        title = "Tesla's electro-magnetic motor",
        originalTitle = "Electro-Magnetic Motor",
        author = "Nikola Tesla",
        year = 1888,
        summary = "A motor whose windings are fed with alternating currents out of step, which makes a rotating field that turns the rotor with no commutator.",
        details = "Tesla filed the application on 12 October 1887, and the patent US 381,968 was granted on 1 May 1888. The motor used polyphase currents to make a rotating magnetic field, so the rotor needed no commutator. Westinghouse later acquired rights to this patent, and one source dates the transfer to July 1888.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Typewriters",
        title = "Hammond's typewriter with changeable type",
        originalTitle = "Hammond typewriter",
        author = "James Bartlett Hammond",
        year = 1880,
        summary = "A typewriter that printed from a cylinder of type which could be swapped for other faces and alphabets.",
        details = "Hammond introduced his machine in 1880, and its type was carried on a cylinder that could be changed for another alphabet. It appealed to writers who needed foreign letters or special symbols. The record uses 1880 as the date of introduction.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Optical communication",
        title = "The photophone, speech sent on a beam of light",
        originalTitle = "On the Production and Reproduction of Sound by Light",
        author = "Alexander Graham Bell and Charles Sumner Tainter",
        year = 1880,
        summary = "A device that sent speech over a beam of light, with a mirror moved by the voice and a selenium cell at the receiver.",
        details = "Bell and Tainter demonstrated the photophone in 1880, sending speech a short distance on a beam of sunlight. The receiver used a selenium cell whose resistance changed with the light falling on it. Their paper on the subject appeared the same year in the American Journal of Science.",
        rare = true,
    ),

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Cameras",
        title = "The Kodak roll-film camera",
        originalTitle = "Kodak camera",
        author = "George Eastman",
        year = 1888,
        summary = "A small box camera loaded with a roll of film for one hundred exposures, which was sent back to the factory for developing and printing.",
        details = "Eastman introduced the Kodak in 1888, and the whole camera went back to the factory with its film for processing. That return-to-factory service was the business model of the early Kodak. The record uses 1888, the year of the first sale.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Radio experiments",
        title = "Hertz's spark-gap resonator",
        originalTitle = "Ueber sehr schnelle elektrische Schwingungen",
        author = "Heinrich Hertz",
        year = 1887,
        summary = "A loop of wire with a small spark gap that sparked when an electromagnetic wave reached it, which Hertz used to detect the waves.",
        details = "Hertz used a spark transmitter and a resonant loop with a gap in 1886 and 1887 to show that electromagnetic waves travel and reflect like light. His 1887 paper reported the very fast electrical oscillations he had produced. The loop was the detector, and the same idea lies behind later radio receivers.",
        rare = true,
    ),

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Calculators",
        title = "The comptometer, a key-driven adding machine",
        originalTitle = "Comptometer",
        author = "Dorr E. Felt",
        year = 1887,
        summary = "An adding machine with a column of keys for each digit place, which added a number at each key press and carried digits by mechanism.",
        details = "Felt's patent is dated 11 October 1887, and the machine became the basis for a long line of key-driven calculators. Sources differ on the date of the first prototype, with 1884 the most common and 1885 also given. The record uses the patent date.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Vehicles",
        title = "Benz's Patent-Motorwagen",
        originalTitle = "Patent-Motorwagen",
        author = "Carl Benz",
        year = 1886,
        summary = "A three-wheeled car with a petrol engine, for which Carl Benz applied for a patent in January 1886.",
        details = "Benz applied for the patent in January 1886, and the car was shown in public in 1886 and again in 1888. Other inventors also claimed early motor cars, so the record makes no claim of being first. It is one of the earliest cars built as a whole vehicle around its engine.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.DEVICE,
        section = "Scanning",
        title = "Nipkow's scanning disc for pictures",
        originalTitle = "Elektrisches Teleskop",
        author = "Paul Nipkow",
        year = 1884,
        summary = "A spinning disc with a spiral of holes that scanned a picture line by line into a series of light values, patented in Germany in 1884.",
        details = "Nipkow's German patent of 1884 described an electric telescope, a rotating perforated disc that split an image into successive points. The patent gave no working receiver of the kind later built, so the device stayed a patent idea for years. The disc is the ancestor of the mechanical television of the 1920s.",
        rare = true,
    ),
)
