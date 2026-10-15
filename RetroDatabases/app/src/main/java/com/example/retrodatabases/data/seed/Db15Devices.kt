package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1890 to 1900: radio detectors, display tubes, calculators, telephone coils and early moving pictures. */
val db15Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "Radio detection",
        title = "Popov's lightning recorder and coherer receiver",
        originalTitle = "Lightning recorder, demonstrated in St Petersburg (1895)",
        author = "Alexander Popov",
        year = 1895,
        summary = "A receiver that used a metal-filings coherer to detect electric waves from lightning, shown in St Petersburg in May 1895.",
        details = "Popov demonstrated the receiver on 7 May 1895, and the day is still marked in Russia as Radio Day. Marconi's 1896 patent for a similar coherer system is part of the dispute over who first made radio detection work.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "Display",
        title = "Braun's cathode-ray tube",
        originalTitle = "Ueber ein Verfahren zur Demonstration und zum Studium des zeitlichen Verlaufs variabler Stroeme (1897)",
        author = "Ferdinand Braun",
        year = 1897,
        summary = "A glass tube with a fluorescent screen, where a magnetic field deflected a beam to draw the shape of a changing current.",
        details = "Braun described the tube in 1897 as a way to study how a current changes with time, and it became the ancestor of the oscilloscope and the television display. The date is that of the paper, and the later forms of the tube are not covered here.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "Calculators",
        title = "Brunsviga pinwheel calculator",
        originalTitle = "Brunsviga, a pinwheel calculator on Odhner's design (1892)",
        author = "Grimme, Natalis and Co., after Willgodt Odhner",
        year = 1892,
        summary = "A pinwheel calculator built on Odhner's design, which multiplied by repeated addition through a wheel of retractable pins.",
        details = "Odhner's machine is dated to patents of 1878 and 1879 and an improved version to 1890, and some accounts date the design differently. In 1892 Grimme, Natalis and Co. took over Odhner's German factory, and the first Brunsviga is reported delivered in July 1892.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "High frequency",
        title = "Tesla's high-frequency coil",
        originalTitle = "Experiments with Alternate Currents of Very High Frequency and Their Application to Methods of Artificial Illumination (1891)",
        author = "Nikola Tesla",
        year = 1891,
        summary = "A resonant transformer that made alternating currents of very high frequency, which Tesla showed in a lecture at Columbia College in 1891.",
        details = "Tesla's lecture of February 1891 described the coil and its use for lighting, and the coil later bore his name. Earlier coil-like experiments by others are not covered here.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "Telephone lines",
        title = "Pupin's loading coil for telephone lines",
        originalTitle = "Loaded telephone line, patent of 1900",
        author = "Michael Idvorsky Pupin",
        year = 1899,
        summary = "Coils placed at intervals along a telephone line added inductance, so voice signals travelled farther with less loss.",
        details = "Pupin and George Ashley Campbell both worked on loading coils in 1899, and the rights were disputed. Pupin's patent is dated 1900.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "Moving pictures",
        title = "Kinetoscope peep-show viewer",
        originalTitle = "Kinetoscope, Edison's peep-show viewer (1891)",
        author = "Thomas Edison and William Kennedy Laurie Dickson",
        year = 1891,
        summary = "A cabinet viewer in which a loop of perforated film moved past a lamp and a shutter, so one person at a time saw a short moving picture.",
        details = "Edison's team built the Kinetoscope in 1891 with Dickson's film work, and public parlours opened in 1894. The date depends on whether the 1888 caveat for the Kinetograph or the 1891 viewer counts as the first public form, and this record uses 1891.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "Radiation",
        title = "Roentgen's X-ray tube",
        originalTitle = "Ueber eine neue Art von Strahlen (1895)",
        author = "Wilhelm Conrad Roentgen",
        year = 1895,
        summary = "An evacuated glass tube with a high voltage across it, which gave off rays that passed through paper and showed bones on a photographic plate.",
        details = "Roentgen found the rays in November 1895 and announced them in December 1895. The tube belongs to the same family of evacuated tubes as Braun's, so the two records are read together.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.DEVICE,
        section = "Measurement",
        title = "Duddell's oscillograph",
        originalTitle = "Oscillograph (1898)",
        author = "William Duddell",
        year = 1898,
        summary = "A loop galvanometer whose light spot was recorded on photographic paper, which drew the waveform of an alternating current.",
        details = "Duddell's oscillograph is dated here to about 1898. It is listed among the instruments that showed electric waves, next to Braun's tube and Roentgen's tube.",
    ),
)
