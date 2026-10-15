package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1870 to 1880: telegraph, railway signalling, telephone switching, arc lighting and electric traction. */
val db17Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.SYSTEM,
        section = "Telegraph",
        title = "Edison's quadruplex telegraph",
        originalTitle = "Quadruplex telegraph",
        author = "Thomas Alva Edison",
        year = 1874,
        summary = "A telegraph system that sent two messages in each direction at the same time over one wire, by adding a second pair of signals to a duplex circuit.",
        details = "Edison's patent for the quadruplex is dated 1874, and the system combined two duplex circuits on one line. It let a telegraph company double the traffic on a line without laying a second wire.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.SYSTEM,
        section = "Railway signalling",
        title = "Tyer's electric train tablet",
        originalTitle = "Electric train tablet",
        author = "Edward Tyer",
        year = 1878,
        summary = "A signalling system for single-track railways in which a driver needs a token called the tablet to enter a section, so two trains cannot share it.",
        details = "Tyer's 1878 patent covered electric control of single-line working, and the design followed a rail disaster at Thorpe in 1874 that killed 21 people. The method entered regular service in 1880 on the Dalmally to Oban line in Scotland.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.SYSTEM,
        section = "Telephone switching",
        title = "First commercial telephone exchange, New Haven",
        originalTitle = "First commercial telephone exchange",
        author = "District Telephone Company, New Haven",
        year = 1878,
        summary = "The switchboard that let paying subscribers reach one another through a single exchange, opened in a New Haven storefront in January 1878.",
        details = "The District Telephone Company was founded in January 1878, and the exchange opened on 28 January with 21 customers, each paying a monthly fee. The switchboard was improvised, and each call needed several separate connections, so only a pair of conversations could run at once.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.SYSTEM,
        section = "Electric lighting",
        title = "Brush arc lighting in Cleveland",
        originalTitle = "Brush arc lighting system",
        author = "Charles Francis Brush",
        year = 1879,
        summary = "A street lighting system in which dynamos fed arc lamps over a public square.",
        details = "Brush arc lamps lit Public Square in Cleveland in 1879, one of the first commercial street lighting installations in the United States. The lamps were fed by dynamos that Brush designed.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.SYSTEM,
        section = "Electric traction",
        title = "Siemens electric railway at the Berlin exhibition",
        originalTitle = "Elektrische Eisenbahn (1879)",
        author = "Werner von Siemens",
        year = 1879,
        summary = "A small electric railway that carried passengers on a short track at the Berlin trade exhibition, with its power from a dynamo.",
        details = "Siemens and Halske built the line for the Berlin trade exhibition of 1879. It was one of the first electric railways to carry passengers, and it showed that a locomotive could draw its current from a fixed generator.",
    ),
)
