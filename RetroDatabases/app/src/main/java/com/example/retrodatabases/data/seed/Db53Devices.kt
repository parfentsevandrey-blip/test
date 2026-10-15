package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1510 to 1520: a Nuremberg pocket watch, Etzlaub's sundials, Stoeffler's astrolabe, Leonardo's lion, a wheel lock, Kobel's counting table and a memory treatise. */
val db53Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Henlein's Nuremberg egg, a spring-driven pocket watch",
        originalTitle = "Nuremberger Ei",
        author = "Peter Henlein",
        year = 1510,
        summary = "Peter Henlein's spring-driven pocket watch, called the Nuremberg egg, dates from about 1510 and is among the first portable watches.",
        details = "The watch ran on a mainspring instead of hanging weights, so it could be carried in a pocket. Accounts differ on the exact date, which is put in the early 1500s, and on how many watches of this kind survive.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Sundials",
        title = "Etzlaub's pocket sundial with a road map, 1511",
        originalTitle = "Sundial of 1511",
        author = "Erhard Etzlaub",
        year = 1511,
        summary = "Erhard Etzlaub's pocket sundial of 1511 carries a small map on a hinged lid, and one of the two known examples is held by the Germanisches Nationalmuseum in Nuremberg.",
        details = "An older encyclopedia describes the lid map as about 93 by 65 millimetres. Etzlaub also made the road map to Rome, usually dated to about 1500, which is the better known of his maps.",
        rare = true,
    ),

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Sundials",
        title = "Etzlaub's sundial of 1513 with a chart of Europe and North Africa",
        originalTitle = "Sundial of 1513",
        author = "Erhard Etzlaub",
        year = 1513,
        summary = "A sundial of 1513 credited to Erhard Etzlaub carries a chart of Europe and North Africa, so that a traveller can set the dial to his own place.",
        details = "The dial is in the collection of the Adler Planetarium, and the record does not say where it was made. The chart lets the user orient the dial to his location before reading the time.",
        rare = true,
    ),

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Stoeffler's astrolabe, the Elucidatio of 1513",
        originalTitle = "Elucidatio fabricae ususque astrolabii",
        author = "Johannes Stoeffler",
        year = 1513,
        summary = "Stoeffler's Elucidatio of 1513 explains how an astrolabe is built and used, with its plates, rete and scales set out for the reader.",
        details = "Stoeffler taught mathematics and astronomy at Tubingen, and his book is a teaching text for the astrolabe, the instrument that measured altitudes and read the time from the stars. Accounts differ on how far the book describes his own instrument rather than the general form of the astrolabe.",
        rare = true,
    ),

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "Leonardo's mechanical lion, shown to Francis I in 1515",
        originalTitle = "Leone meccanico",
        author = "Leonardo da Vinci",
        year = 1515,
        summary = "Leonardo built a mechanical lion that walked forward and opened its chest to show lilies when King Francis I was welcomed in 1515.",
        details = "Vasari's account is the main source for the figure, and accounts differ on the place where it was shown and on how it moved. It is one of the best-known automata of the Renaissance.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Firearms",
        title = "The wheel lock, a spring-driven spark for guns, about 1510",
        originalTitle = "Wheel lock",
        author = "Gunmakers of Nuremberg and Italy",
        year = 1510,
        summary = "The wheel lock struck sparks from iron pyrite with a spring-driven wheel, an early mechanical ignition device for guns that appeared about 1510.",
        details = "Its use spread in the years around 1510, and accounts differ on whether Nuremberg or an Italian workshop saw the first working locks. It replaced the slow match and is one of the early devices that stored power in a spring.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Counting",
        title = "Kobel's counting table, the board of his 1514 reckoning",
        originalTitle = "Ein neu geordnet Rechenbiechlin",
        author = "Jakob Kobel",
        year = 1514,
        summary = "Kobel's booklet of 1514 relied on a table abacus, a board with ruled lines on which counters were laid to show each value.",
        details = "The lines of the board were worth 1, 10, 100 and 1000 from the bottom up. Accounts differ on the printing places and the editions of the booklet.",
        rare = true,
    ),

    entry(
        databaseId = 53,
        kind = Kind.DEVICE,
        section = "Memory",
        title = "Romberch's Congestorium, a mnemonic device for the memory",
        originalTitle = "Congestorium artificiose memorie",
        author = "Johannes Romberch",
        year = 1520,
        summary = "Romberch's Congestorium of 1520 is a treatise on the art of memory, a set of mnemonic methods for fixing images in the mind.",
        details = "Its methods used places and figures that the reader already knew, so the images could be called up in order. The treatise belongs to the printed guides to memory that the Renaissance produced in quantity.",
    ),

)
