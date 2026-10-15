package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1900 to 1910: the valves, detectors, recorders and instruments that carried the decade's signals and data. */
val db14Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Recording",
        title = "Telegraphone magnetic wire recorder",
        originalTitle = "Telegraphone, Valdemar Poulsen's magnetic wire recorder (patent 1898)",
        author = "Valdemar Poulsen",
        year = 1900,
        summary = "A machine that records speech as a magnetic pattern on steel wire, which can be played back: one of the first magnetic sound recorders.",
        details = "Poulsen recorded speech on steel piano wire with an electromagnet fed from a microphone. The machine was shown at the Paris Exposition of 1900, and accounts differ on whether it was run there and on the prize it received.",
        rare = true,
    ),

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Electron tubes",
        title = "Wehnelt cylinder in the electron gun",
        originalTitle = "Wehnelt cylinder, control electrode for the electron beam (1903)",
        author = "Arthur Wehnelt",
        year = 1903,
        summary = "A negatively charged cup with a small hole sits around the cathode, controls how many electrons leave it, and focuses the beam.",
        details = "Wehnelt introduced the cylinder in 1903. The same electrode became the control grid of cathode ray tubes and of the electron guns used in later displays and microscopes. The German biography of Wehnelt credits him with the 1903 introduction.",
        rare = true,
    ),

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Electron tubes",
        title = "Fleming valve, the two-electrode tube",
        originalTitle = "Fleming valve, British patent of 1904",
        author = "John Ambrose Fleming",
        year = 1904,
        summary = "A heated filament and a plate inside a vacuum pass current in one direction only, so the valve can detect radio signals.",
        details = "Fleming patented the valve in 1904 as a detector for wireless signals, and it was the first thermionic valve. It had two electrodes, so it rectified but could not amplify.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Electron tubes",
        title = "De Forest's Audion, the three-electrode tube",
        originalTitle = "Audion, three-electrode vacuum tube (De Forest, 1906)",
        author = "Lee de Forest",
        year = 1906,
        summary = "A grid placed between the filament and the plate lets a small signal control a larger current, so the tube can amplify.",
        details = "De Forest filed for the device in October 1906, and the name Audion followed. The grid is what the Fleming valve lacked, and it made amplification and oscillation possible.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Radio",
        title = "Pickard's silicon crystal detector",
        originalTitle = "Silicon crystal detector, patent filed in August 1906",
        author = "Greenleaf Whittier Pickard",
        year = 1906,
        summary = "A fine wire touching a silicon crystal rectifies radio signals, and the detector needs no bias battery.",
        details = "Pickard filed his silicon detector in August 1906, and a company to sell such detectors was formed in 1907. Before choosing silicon he had screened many minerals for rectifying behavior.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Medical instruments",
        title = "Einthoven's string galvanometer",
        originalTitle = "String galvanometer for recording the electrocardiogram (Einthoven, 1901)",
        author = "Willem Einthoven",
        year = 1901,
        summary = "A very fine string moves in a magnetic field as the heart's currents pass through it, and the movement is recorded as a trace over time.",
        details = "The instrument records voltage over time directly, which the older capillary electrometer could only do after long calculations. The first results appeared in 1901 and 1902, and Einthoven received the Nobel Prize in 1924 for this work.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Power",
        title = "Mercury-arc rectifier",
        originalTitle = "Mercury vapour rectifier, Cooper Hewitt design (1902)",
        author = "Peter Cooper Hewitt",
        year = 1902,
        summary = "A glass tube with a mercury cathode passes current in one direction only, so alternating current becomes direct current with no moving parts.",
        details = "The design grew from Cooper Hewitt's mercury-vapour lamp and was the first rectifier that turned alternating current into direct current without moving parts. It ran direct-current motors and battery chargers from alternating supplies.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Gyrocompass for ships",
        originalTitle = "Meridian gyrocompass, Anschutz and Co., Kiel (1908)",
        author = "Hermann Anschutz-Kaempfe",
        year = 1908,
        summary = "A spinning rotor settles toward true north, so a ship's heading comes from a gyroscope instead of a magnetic compass.",
        details = "The firm was set up in Kiel in 1905, and its meridian gyrocompass was tested at sea in 1908 aboard the German flagship. Sources give 1906, 1907 and 1908 for different stages, and this record uses 1908 for the sea trials.",
    ),
)
