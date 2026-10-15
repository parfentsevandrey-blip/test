package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1620 to 1630: two slide rules and a calculating clock, Gunter's sector and chain, and Drebbel's boat and thermostat. */
val db42Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Gunter's line of numbers, the Gunter scale",
        originalTitle = "The Description and Use of the Sector, the Crosse-staffe and other Instruments",
        author = "Edmund Gunter",
        year = 1623,
        summary = "A logarithmic scale on a rule which, with a pair of dividers, multiplies and divides by adding and subtracting lengths.",
        details = "Gunter's scale was printed in his 1623 book on the sector and the cross-staff. Accounts differ on the date of the idea, which some place in 1620, but the printed description of 1623 is the date used here. The scale is the ancestor of the slide rule.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Oughtred's slide rule",
        originalTitle = "Circles of Proportion and the Horizontal Instrument",
        author = "William Oughtred",
        year = 1622,
        summary = "Two scales that slide against each other multiply and divide by adding and subtracting lengths, and Oughtred's form is dated to 1622.",
        details = "The date 1622 is the date given for the invention of the rectilinear rule, which was printed only in 1633, while the circular form was printed in 1632. Accounts differ on the date of invention, and Richard Delamain later claimed a similar circular rule.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Delamain's circular slide rule",
        originalTitle = "Grammelogia",
        author = "Richard Delamain",
        year = 1630,
        summary = "Delamain's circular rule of 1630 is a calculating device with sliding circular scales, and its date and originality were disputed with Oughtred.",
        details = "Delamain's manuscript reached Charles I in 1629, and the book was printed in 1630 or 1631, with the printing date given differently by different sources. The quarrel with Oughtred over who devised the circular form ran for years after the printing.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Schickard's calculating clock",
        originalTitle = "Rechenuhr, letter to Kepler",
        author = "Wilhelm Schickard",
        year = 1623,
        summary = "Schickard's calculating clock adds numbers with toothed wheels that carry a ten into the next wheel, and it was described to Kepler in a letter of 1623.",
        details = "The letter to Kepler was dated September 1623 and described the machine with drawings. A letter of February 1624 reports that a fire destroyed a half-built second machine meant for Kepler.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Boats",
        title = "Drebbel's rowing submarine on the Thames",
        originalTitle = "A rowing boat under water, built for King James I",
        author = "Cornelis Drebbel",
        year = 1620,
        summary = "A wooden rowing boat sheathed in greased leather, built by Drebbel for King James I, was rowed under the Thames in 1620 and is the earliest submersible with credible construction records.",
        details = "Accounts differ on the number of oars and rowers, with four and six oars given in some sources and twelve rowers in others. The air supply is disputed, and the king's ride is reported in some accounts and doubted in others.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Gunter's sector for proportional scaling",
        originalTitle = "The Description and Use of the Sector",
        author = "Edmund Gunter",
        year = 1623,
        summary = "A pair of sector arms with marked lines that scales a length in a chosen ratio, described in Gunter's book of 1623 alongside his scale.",
        details = "The 1623 book describes the sector and the cross-staff together, and the same work contains Gunter's line of numbers. The sector is a different instrument from the scale, though the two share a book and a maker.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Incubators",
        title = "Drebbel's thermostat for hatching eggs",
        originalTitle = "Incubator for hatching eggs",
        author = "Cornelis Drebbel",
        year = 1620,
        summary = "Drebbel is credited with a thermostat that held the heat of an incubator steady, so that eggs could be hatched by it.",
        details = "Drebbel is credited with a regulated incubator, usually dated to about 1620, and in some accounts the regulator is a mercury thermostat. Accounts differ on how the thermostat worked and on when the incubator was built.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "Gunter's surveyor's chain of 66 feet",
        originalTitle = "Gunter's chain of 100 links",
        author = "Edmund Gunter",
        year = 1620,
        summary = "A surveyor's chain 66 feet long and divided into 100 equal links, which let land be measured in decimal parts of a chain and reckoned in acres.",
        details = "The chain was 66 feet long and divided into 100 equal links, so ten square chains make an acre and a field's area can be reckoned in decimal parts of a chain. The chain is usually dated to the 1620s, and accounts differ on when it was first described, so the year given here is approximate.",
    ),

)
