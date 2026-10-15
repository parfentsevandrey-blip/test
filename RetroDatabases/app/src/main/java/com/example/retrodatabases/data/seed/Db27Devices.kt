package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1770 to 1780: writing and chess automata, a spinning mule, calculating and dividing machines, a steam dray, a boring mill and an electrophorus. */
val db27Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "Jaquet-Droz's writing automaton, The Writer",
        originalTitle = "L'Ecrivain",
        author = "Pierre Jaquet-Droz with Henri-Louis Jaquet-Droz and Jean-Frederic Leschot",
        year = 1772,
        summary = "A clockwork figure of a boy that dipped a real pen in ink and wrote a short message, the text set by cams that could be changed.",
        details = "Pierre Jaquet-Droz, a Swiss watchmaker, completed the figure in 1772 with his son and the mechanic Leschot, and it is now kept in Neuchatel. Its cams could be replaced to change the text, and it wrote up to forty characters on paper. Accounts of the family's three figures vary, and 1772 is the date usually given for the Writer.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "Kempelen's chess automaton, the Turk",
        originalTitle = "Schachtuerke",
        author = "Wolfgang von Kempelen",
        year = 1770,
        summary = "A chess-playing figure in Turkish dress that beat many opponents, while a human operator sat hidden inside its cabinet.",
        details = "Kempelen showed the figure at the court of Maria Theresa in Vienna in 1770, and it toured Europe and America for more than eighty years. The cabinet hid a human player, and that concealment was the secret of the act. The figure was destroyed by a fire in Philadelphia in 1854.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Spinning",
        title = "Crompton's spinning mule",
        originalTitle = "Crompton's spinning mule",
        author = "Samuel Crompton",
        year = 1779,
        summary = "A spinning machine that combined the drawing rollers of the water frame with the moving carriage of the jenny to spin fine yarn.",
        details = "Crompton built the mule in 1779 in Lancashire and did not patent it, so the machine spread quickly and its inventor earned little from it at first. The carriage drew out the roving and then wound the thread onto many spindles, which gave fine and strong yarn. Accounts give 1779 for the first working mule, though some put the completion a year later.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Hahn's calculating machine for four operations",
        originalTitle = "Rechenmaschine",
        author = "Philipp Matthaeus Hahn",
        year = 1774,
        summary = "A clergyman's calculating machine, built with gears and cylinders, that could add, subtract, multiply and divide.",
        details = "Hahn was a clergyman and clockmaker at Echterdingen near Stuttgart, and he built working calculating machines for the four operations. Accounts differ on when the first working machine existed, since 1770, 1773, 1774 and 1778 all appear in histories, and 1774 is the year used here. The number of machines he made is not firmly known.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Instrument making",
        title = "Ramsden's dividing engine for graduating instruments",
        originalTitle = "Description of an engine for dividing mathematical instruments",
        author = "Jesse Ramsden",
        year = 1775,
        summary = "A machine that marked the scales of instruments by turning a precise screw, so that the divisions came out evenly spaced.",
        details = "Ramsden's improved engine of 1775 divided the circles and scales of surveying and astronomical instruments, and his description of it was published in 1777 by the Board of Longitude. Accounts differ on the date of the first engine, which some give as 1766 and others as 1767. The payment made for the engine is also reported with different sums.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Road vehicles",
        title = "Cugnot's steam dray",
        originalTitle = "Fardier a vapeur",
        author = "Nicolas-Joseph Cugnot",
        year = 1770,
        summary = "A three-wheeled steam tractor built to haul artillery, which was tried in Paris in 1770 and is often called the first self-propelled road vehicle.",
        details = "Cugnot built the carriage for the French army, with its boiler in front, and it was tried in Paris in 1770. Accounts differ on the build date, which some give as 1769. A later trial in 1771 ended with the vehicle striking a wall, and the machine was judged too heavy and unsteady, so it did not enter army service.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Boring",
        title = "Wilkinson's boring mill for steam cylinders",
        originalTitle = "Wilkinson's boring mill for steam cylinders",
        author = "John Wilkinson",
        year = 1774,
        summary = "A boring machine for iron cylinders that bored them true enough for Watt's steam engines to run with little leakage.",
        details = "Wilkinson's mill bored the cylinders of the Boulton and Watt engines more accurately than earlier methods, and 1774 is the date usually given for it. Accounts differ on when the first Boulton and Watt cylinder was bored on it. The mill is cited as one of the first machine tools built to a fine tolerance.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "Volta's electrophorus for static charge",
        originalTitle = "Elettroforo perpetuo",
        author = "Alessandro Volta",
        year = 1775,
        summary = "A resin disc and a metal plate that held a static charge which could be drawn off again and again, giving sparks in lectures.",
        details = "Volta described the electrophorus in letters of 1775 and 1776, and it was used in electrical lectures for a century. Accounts differ on whether Johan Carl Wilcke had described a similar device earlier, in 1762. The date given here is that of Volta's first description.",
    ),

)
