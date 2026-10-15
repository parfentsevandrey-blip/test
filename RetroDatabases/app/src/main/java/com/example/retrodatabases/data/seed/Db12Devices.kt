package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1920 to 1930: calculators, cipher machines, a quartz resonator, a printing telegraph and a teleprinter. */
val db12Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Calculators",
        title = "Torres Quevedo's electromechanical arithmometer",
        originalTitle = "Arithmometre electromecanique (Torres Quevedo, 1920)",
        author = "Leonardo Torres Quevedo",
        year = 1920,
        summary = "A calculating machine with electromagnetic action, shown in Paris in 1920, whose arithmetic unit could stand apart from its keyboard and typewriter.",
        details = "Torres showed it in Paris in 1920, to mark the hundredth anniversary of Thomas de Colmar's arithmometer. The operator keyed in commands on a typewriter that could sit apart from the arithmetic unit and received printed results. The machine covered all four basic operations. Accounts differ on whether the showing was at the Academy of Sciences or at a conference.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Frequency",
        title = "Cady's quartz crystal oscillator",
        originalTitle = "Piezoelectric quartz oscillator (Cady, 1921 to 1923)",
        author = "Walter Guyton Cady",
        year = 1921,
        summary = "A quartz resonator used as a frequency standard, first described in 1921 by a physicist at Wesleyan University.",
        details = "Cady's work at Wesleyan University produced the first quartz-resonator frequency circuit, dated to 1921 by most sources, though one list gives 1922. A February 1921 talk to the American Physical Society proposed the resonator as a frequency standard. In December 1921 the first piezo-oscillator circuit was shown, with a quartz bar running near 70 kHz. The IEEE gave the work a Milestone for 1921 to 1923.",
        rare = true,
    ),

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Cipher machines",
        title = "US Army M-94 cipher device",
        originalTitle = "Cipher device M-94, a rod of 25 disks",
        author = "Parker Hitt and Joseph Mauborgne",
        year = 1922,
        summary = "The United States Army's cylinder cipher device of 25 disks, adopted in 1922 and used into the Second World War.",
        details = "Sources date the Army's formal adoption to 1922. The device is a rod carrying 25 disks, each with a jumbled alphabet, and the Navy fielded it as CSP 488. Accounts disagree on its origin: Hitt is credited with the concept, dated to 1912 by one source, while Mauborgne's redesign is dated to 1917. Retirement dates range from about 1942 to 1945, when electromechanical rotor machines took over.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Cipher machines",
        title = "Commercial Enigma",
        originalTitle = "Enigma, the commercial cipher machine of Chiffriermaschinen AG (1923)",
        author = "Arthur Scherbius",
        year = 1923,
        summary = "The commercial cipher machine marketed by Chiffriermaschinen AG from 1923, before the military versions of the 1930s.",
        details = "Chiffriermaschinen AG was founded in 1923 as the successor to an earlier venture of Scherbius, and the Enigma name appeared in the second half of 1923, with the earliest known use dated 1 September 1923. The first trade machine was large and heavy, with an integrated typewriter, and one source gives its weight as about 50 kg. Model names and dates for A, B and C are disputed between 1924 and 1926.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Cipher machines",
        title = "Kryha cipher machine",
        originalTitle = "Kryha Standard and Liliput cipher machines (1924)",
        author = "Alexander von Kryha",
        year = 1924,
        summary = "A clockwork cipher machine sold from the mid-1920s in standard, electric and pocket versions.",
        details = "Kryha's German and US patent applications date from 1924. The pocket Liliput is dated to 1925 by one listing, and it used the same algorithm as the standard model, so the two could communicate. In 1933 United States officials had William Friedman and three colleagues test the machine, and they read a 1,135-character test message in about two hours and forty minutes.",
        rare = true,
    ),

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Cipher machines",
        title = "Hagelin B-21 cipher machine",
        originalTitle = "B-21 by AB Cryptograph (Hagelin, 1925)",
        author = "Boris Hagelin",
        year = 1925,
        summary = "A portable cipher machine from the Swedish firm AB Cryptograph, built with pinwheels that govern the stepping of its rotors.",
        details = "Several sources date the prototype to 1925, when the Swedish military asked for an Enigma substitute. Wikipedia and Cryptomuseum put the Swedish order in 1928, so the dates are disputed. Hagelin joined the firm as a controller in 1922 and ran it by 1925. The design reworked Arvid Damm's B-13 concept with a battery, lamps and Hagelin's own pinwheels.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Printing telegraph",
        title = "Hellschreiber",
        originalTitle = "Hellschreiber, a dot-matrix printing telegraph (Hell, 1929)",
        author = "Rudolf Hell",
        year = 1929,
        summary = "A printing telegraph that sends each letter as a column of dots, a forerunner of the fax machine that tolerates radio interference.",
        details = "Sources date the invention to 1925 with a patent in 1929, or to 1929 alone. Siemens-Halske acquired the rights in 1929. Production passed 50,000 machines by 1945, and users included post offices, newsrooms, police and weather services. Hell lived from 1901 to 2002.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.DEVICE,
        section = "Teleprinters",
        title = "Teletype Model 15",
        originalTitle = "Teletype Model 15 teleprinter (1930)",
        author = "Teletype Corporation",
        year = 1930,
        summary = "The teleprinter that became the mainstay of United States military communications in the Second World War, using five-bit Baudot code.",
        details = "The Model 15 was launched in 1930, and its commercial patent application was filed that year. It used the five-bit Baudot code rather than eight-bit ASCII. The Model 14 of 1925 is a different and earlier machine. The Teletype Corporation was founded in 1906, and sources say it became part of AT and T in 1930, while one version names Western Electric as the buyer.",
    ),
)
