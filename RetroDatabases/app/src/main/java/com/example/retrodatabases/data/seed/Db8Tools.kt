package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1970 to 1980: a line editor, the file comparison tool, a word processor, the first spreadsheet and a computer algebra system. */
val db8Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.TOOL,
        section = "Editors",
        title = "ed",
        originalTitle = "The ed line editor for Unix",
        author = "Ken Thompson",
        year = 1971,
        summary = "Ken Thompson's line editor for Unix, written in 1971 and still shipped with Unix-like systems, where it edits one line at a time with short commands.",
        details = "Its commands act on line numbers and text patterns, and the same commands can be typed by hand or read from a script.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.TOOL,
        section = "Version control",
        title = "diff",
        originalTitle = "Unix diff, with the Hunt and McIlroy algorithm",
        author = "James Hunt and Doug McIlroy",
        year = 1976,
        summary = "The Unix tool that finds the smallest set of line changes between two files, using the longest common subsequence of their lines.",
        details = "The tool first appeared in the Fifth Edition of Unix in 1974, and the algorithm was described in a 1976 Bell Labs report. " +
            "Its output could be replayed by the ed editor, so a change could be sent as a short script.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.TOOL,
        section = "Word processing",
        title = "WordStar",
        originalTitle = "WordStar, the word processor for CP/M",
        author = "Rob Barnaby and MicroPro",
        year = 1978,
        summary = "MicroPro's word processor for CP/M machines, which became one of the best-selling programs of its time and was run from the keyboard with control keys.",
        details = "Its commands used the control key with letters, and users learned them as a set. The program ran on the CP/M machines of the late 1970s.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.TOOL,
        section = "Spreadsheets",
        title = "VisiCalc",
        originalTitle = "VisiCalc, the first electronic spreadsheet",
        author = "Dan Bricklin and Bob Frankston",
        year = 1979,
        summary = "Dan Bricklin and Bob Frankston's electronic spreadsheet, released for the Apple II in 1979, which made the personal computer a business tool.",
        details = "A cell held a number or a formula, and a change in one cell updated every cell that depended on it. Business users bought Apple IIs to run it, and many histories count that as the start of the business market for microcomputers.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.TOOL,
        section = "Computer algebra",
        title = "Maple",
        originalTitle = "Maple computer algebra system, begun at the University of Waterloo",
        author = "Keith Geddes, Gaston Gonnet and the Waterloo team",
        year = 1980,
        summary = "A computer algebra system begun at the University of Waterloo in 1980, after a team looked for a way to run the Macsyma system on cheaper computers.",
        details = "The earliest limited build followed within weeks, and a keynote dates the first system to December 1980. Broader adoption came in 1982, and Maple 3.3, the first version sold to customers, reached buyers in 1984. " +
            "Dates vary with the milestone.",
        rare = true,
    ),
)
