package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1860 to 1870: writing machines, telephones, printers, cable recorders and the instruments that traced signals. */
val db18Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Writing machines",
        title = "Sholes's typewriter patent of 1868",
        originalTitle = "Improvement in Type-Writing Machines, US patent 79,265 (1868)",
        author = "Christopher Latham Sholes, Carlos Glidden and Samuel Soule",
        year = 1868,
        summary = "A keyboard machine that printed one letter at a time by striking a type bar against an inked ribbon, patented in the United States in 1868.",
        details = "The patent was granted on 23 June 1868, and the machine was built in Milwaukee. Accounts differ on whether the keys of the 1868 model were in alphabetical order, and the familiar QWERTY arrangement came with the Remington machine of 1873.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Writing machines",
        title = "Malling-Hansen's writing ball, type rods on a sphere",
        originalTitle = "Skrivekugle, the writing ball (design 1865, patent 1870)",
        author = "Rasmus Johan Malling-Hansen",
        year = 1865,
        summary = "A typewriter whose type rods are set around a hemisphere, so that one ball carries the whole alphabet and each rod strikes the paper at the centre.",
        details = "The design is usually dated to 1865, while the patent date varies between 1865, 1870 and 1871, so this record uses the design year. The machine was shown at exhibitions from 1872, and its inventor was a Danish pastor.",
        rare = true,
    ),

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Logic machines",
        title = "Jevons's logical piano",
        originalTitle = "On the Mechanical Performance of Logical Inference (1870)",
        author = "William Stanley Jevons",
        year = 1869,
        summary = "A keyboard machine in which pressing keys for terms and their contraries sets up premises, and the machine then shows the conclusion that follows from them.",
        details = "Jevons built the piano in 1869 and described it in a paper read in 1870, so both dates are in use and this record uses the build year. The machine applied the substitution rules of the logic of quality, which Jevons had set out in his 1864 Pure Logic.",
        rare = true,
    ),

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Telephony",
        title = "Reis's telephone of 1861",
        originalTitle = "Telephon, lecture to the Frankfurt Physical Society (1861)",
        author = "Johann Philipp Reis",
        year = 1861,
        summary = "A diaphragm and a make-and-break contact sent sound along a wire as electrical pulses, and the device carried musical tones and some speech.",
        details = "Reis presented the Telephon to the Frankfurt Physical Society on 26 October 1861, and public demonstrations became common from 1863. Accounts of the first prototype differ, with dates of 1857 and 1860 both given, and priority is disputed with Meucci and Gray; a 1901 court ruling went Bell's way.",
        rare = true,
    ),

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Telegraph printing",
        title = "Calahan's stock ticker",
        originalTitle = "Stock ticker for the New York Gold Exchange (1867)",
        author = "Edward Augustin Calahan",
        year = 1867,
        summary = "A telegraph printer that wrote stock prices as text on a paper tape, so brokers in distant offices could follow the market without a messenger.",
        details = "Calahan's instrument was in use on the New York Gold Exchange from 1867. Later improvements by other inventors are separate items, and the record gives the first working date only.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Cable signals",
        title = "Kelvin's siphon recorder for cable signals",
        originalTitle = "Siphon recorder for submarine telegraph cables (1867)",
        author = "William Thomson, later Lord Kelvin",
        year = 1867,
        summary = "A fine glass siphon carrying ink traced the cable signals as a wavy line on a moving paper tape, so slow signals could be read at the receiving end.",
        details = "Thomson patented the recorder in 1867, and it became the usual receiver on long submarine cables in the following years. It replaced the mirror galvanometer that Thomson had used earlier for cable signals, so the two instruments are linked but separate.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Physiology",
        title = "Marey's sphygmograph, the pulse as a curve",
        originalTitle = "Sphygmographe, instrument for recording the pulse (1860)",
        author = "Etienne-Jules Marey",
        year = 1860,
        summary = "A lever resting on the wrist moved a pen that traced the pulse as a curve on smoked paper, so the shape of each beat could be measured and compared.",
        details = "Marey described the instrument in 1860, and it was among the first recorders of body signals on a moving paper. Physiologists who followed him used the same idea of tracing a signal on a moving surface.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.DEVICE,
        section = "Telegraph",
        title = "Wheatstone's automatic telegraph with paper tape",
        originalTitle = "Automatic telegraph, perforated paper tape sent at high speed (1867)",
        author = "Charles Wheatstone",
        year = 1867,
        summary = "Messages were punched into a paper tape, which a transmitter read at high speed, so the line carried a message faster than a hand key could send it.",
        details = "Wheatstone's system was patented in 1858 and was first used on British lines in the 1860s. This record uses 1867 as the year of its wider service, and accounts of the speed of the system vary, so no figure is given.",
    ),

)
