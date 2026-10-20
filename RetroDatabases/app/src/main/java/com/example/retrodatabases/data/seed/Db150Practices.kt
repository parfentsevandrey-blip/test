package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 540 to 550: the copying of books by hand at Vivarium, the monastery Cassiodorus set up in southern Italy about 540. */
val db150Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.PRACTICE,
        section = "Latin",
        title = "The copying of books by hand at Vivarium, the monastery that Cassiodorus set up in southern Italy about 540",
        originalTitle = "Vivarium",
        author = "Cassiodorus, founder",
        year = 540,
        summary = "At Vivarium, the monastery Cassiodorus set up about 540, monks copied books by hand and kept a library of copies for later readers.",
        details = "Dates for the foundation vary by a few years, so 540 is an anchor for the practice rather than the date of a particular copy. The practice is known mainly from Cassiodorus's own writing about his monastery, and it is a well-known early case of organised book copying in western monasteries.",
        rare = true,
    ),

)
