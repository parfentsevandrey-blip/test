package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1840 to 1850: ternary and analytical machines, electric clocks and cells, a sewing machine, a rotary press and the pointer and chemical telegraphs. */
val db20Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Ternary calculators",
        title = "Fowler's ternary calculating machine",
        originalTitle = "Ternary calculating machine",
        author = "Thomas Fowler",
        year = 1840,
        summary = "Fowler's calculating machine worked in balanced ternary, with digit wheels that each show one of three states instead of ten.",
        details = "Fowler showed the machine to the Royal Society in May 1840. A later version went to a museum in London, and the original machine is lost.",
        rare = true,
    ),

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Electrical clocks",
        title = "Bain's electric clock",
        originalTitle = "Electric clock, patent of 1841",
        author = "Alexander Bain",
        year = 1841,
        summary = "Bain's clock was driven by electric pulses from a battery, so it did not depend on a wound spring, and it is often counted as an early electric clock.",
        details = "Bain patented the clock in 1841. The electric impulses acted on the pendulum, which made the clock a forerunner of later synchronised clock systems.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Batteries",
        title = "Bunsen's carbon cell",
        originalTitle = "Carbon cell with nitric acid",
        author = "Robert Wilhelm Bunsen",
        year = 1841,
        summary = "Bunsen's cell used a carbon plate in place of the platinum of earlier cells, which gave a cheap and steady source of current.",
        details = "Bunsen described the carbon cell in 1841. It was used widely in laboratory and telegraph circuits in the decades that followed.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Sewing machines",
        title = "Howe's lockstitch sewing machine",
        originalTitle = "Lockstitch sewing machine",
        author = "Elias Howe",
        year = 1846,
        summary = "Howe's machine made a lockstitch with a needle whose eye sits near the point and a shuttle that carries the second thread.",
        details = "Howe received a United States patent for the machine in 1846. The eye-pointed needle and shuttle of this design became the basis of the lockstitch machines that followed.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Mechanical computing",
        title = "Babbage's Analytical Engine, first printed account",
        originalTitle = "Notions sur la machine analytique de M. Charles Babbage",
        author = "Charles Babbage, described by Luigi Menabrea",
        year = 1842,
        summary = "The Analytical Engine was a planned general-purpose machine with a mill for operations and a store for numbers, to be driven by punched cards.",
        details = "Babbage worked on the design from the 1830s and explained it at Turin in 1840. Menabrea's memoir of 1842 gave the first printed account, and the machine was never completed in Babbage's lifetime.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Printing machines",
        title = "Hoe's rotary printing press",
        originalTitle = "Rotary printing press",
        author = "Richard March Hoe",
        year = 1843,
        summary = "Hoe's rotary press printed from curved plates on a revolving cylinder, so paper could be fed in a continuous stream instead of sheet by sheet.",
        details = "Hoe built a rotary press in 1843. Machines of this kind later printed newspapers far faster than flat-bed presses could.",
        rare = true,
    ),

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Electric telegraph",
        title = "Bain's electro-chemical telegraph",
        originalTitle = "Electro-chemical telegraph, patent of 1846",
        author = "Alexander Bain",
        year = 1846,
        summary = "Bain's telegraph wrote signals as chemical marks on moving paper, and its inventor claimed it was faster than Morse's system.",
        details = "The receiver left a Prussian blue mark on paper treated with a chemical solution. Bain and Morse fought a patent dispute over the method, and Morse's side won.",
    ),

    entry(
        databaseId = 20,
        kind = Kind.DEVICE,
        section = "Electric telegraph",
        title = "Siemens's pointer telegraph",
        originalTitle = "Zeigertelegraph, the pointer telegraph",
        author = "Werner von Siemens",
        year = 1847,
        summary = "A receiver with a needle that pointed to each letter in turn let operators read messages without learning a code.",
        details = "Siemens built the pointer telegraph in 1847. His workshop with Johann Georg Halske opened in October 1847.",
    ),
)
