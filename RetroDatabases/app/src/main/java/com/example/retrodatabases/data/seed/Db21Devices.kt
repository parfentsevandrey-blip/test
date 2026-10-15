package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1830 to 1840: relays, induction, needle telegraphs, early motors, a steady battery and the first photographic process. */
val db21Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Telegraph relay",
        title = "Henry's electromagnetic relay",
        originalTitle = "Relay for a long telegraph circuit (1835)",
        author = "Joseph Henry",
        year = 1835,
        summary = "An electromagnet that switches on a local circuit, so a faint signal on a long telegraph line can work a strong local magnet and restore the message.",
        details = "Henry devised the relay in 1835 to answer the loss of current on long lines, where the extra wire resistance cut the signal. Several sources credit him with it, and one account describes a test in which a faint magnet tripped a stronger one across a long line. By then Henry was at Princeton, having left Albany in 1832.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Induction",
        title = "Faraday's induction ring",
        originalTitle = "Experimental Researches in Electricity, first series (1831)",
        author = "Michael Faraday",
        year = 1831,
        summary = "An iron ring wound with two separate coils, which showed that switching a current on or off in one coil induces a current in the other.",
        details = "Faraday made the ring experiment in August 1831, and it gave a clear induced current each time the current in one coil was made or broken. He described it in the first series of his Experimental Researches in Electricity, printed in 1831. The record uses the 1831 date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Needle telegraph",
        title = "Schilling's six-needle telegraph",
        originalTitle = "Schilling's telegraph at St Petersburg (1832)",
        author = "Pavel Schilling",
        year = 1832,
        summary = "A needle telegraph whose six indicators encoded letters and numerals as binary patterns, giving 64 symbols on eight wires in the 1832 set-up at St Petersburg.",
        details = "Schilling's 1832 set-up had six signal wires, a call wire and a shared return, eight conductors in all. The 64 patterns covered letters, numerals and special characters. Schilling died in 1837, and the planned line to Kronstadt was dropped. The record uses the 1832 demonstration.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Electric motor",
        title = "Jacobi's electric motor",
        originalTitle = "Jacobi's electromagnetic rotary engine (1834)",
        author = "Moritz von Jacobi",
        year = 1834,
        summary = "A rotary electric motor in which electromagnets pull against one another in turn, shown in 1834 and later used to drive a boat on the River Neva.",
        details = "Jacobi built the motor in 1834 and kept developing it over the next years. In 1838 a boat powered by an electric motor of his design carried passengers on the Neva at St Petersburg. The record uses the 1834 date for the motor.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Electric motor",
        title = "Davenport's electric engine",
        originalTitle = "Davenport's electric engine, United States patent (1837)",
        author = "Thomas Davenport",
        year = 1834,
        summary = "A small electric motor built by a Vermont blacksmith, which turned a wheel through the pull of electromagnets and was later fitted to a small model track.",
        details = "Davenport built his first motor in 1834 and obtained a United States patent for an electric engine in 1837. Accounts of his early machines differ on their power and on the dates of the tests. The record uses 1834 for the first motor.",
        rare = true,
    ),

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Batteries",
        title = "Daniell's constant cell",
        originalTitle = "On a constant voltaic battery (1836)",
        author = "John Frederic Daniell",
        year = 1836,
        summary = "A two-fluid cell with copper and zinc in separate solutions, which kept a steady voltage for longer than the single-fluid cells that came before it.",
        details = "Daniell published the cell in 1836 to remove the fall in voltage that the simpler cells showed as hydrogen gathered on the copper plate. The cell later became a standard source for telegraph circuits. The record uses the 1836 paper date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Earth return",
        title = "Steinheil's earth-return telegraph",
        originalTitle = "Steinheil's telegraph, Munich (1836)",
        author = "Carl August von Steinheil",
        year = 1836,
        summary = "A telegraph in which the earth itself carried the return current, so one wire could serve a whole circuit, a method found by Steinheil in Munich.",
        details = "Steinheil reported in 1836 that the ground could carry the return current of a telegraph circuit, which removed the need for a second wire. He published his account of the telegraph in 1838. The record uses the 1836 date of the discovery.",
        rare = true,
    ),

    entry(
        databaseId = 21,
        kind = Kind.DEVICE,
        section = "Photography",
        title = "Daguerre's daguerreotype camera",
        originalTitle = "Historique et description des procedes du daguerreotype et du diorama (1839)",
        author = "Louis Daguerre",
        year = 1839,
        summary = "A camera process that fixed an image on a silvered copper plate with mercury vapour and iodine, announced in Paris in 1839 as one of the first practical photographic processes.",
        details = "Daguerre's process was announced in Paris in January 1839 and described in print that year. Priority is disputed with Niepce, whose heliography dates from the 1820s. Talbot's negative process of 1841 came later. The record uses the 1839 announcement.",
    ),
)
