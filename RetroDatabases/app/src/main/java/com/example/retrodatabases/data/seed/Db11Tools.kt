package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1930 to 1940: the tables, instruments, reviewing journals and keypunches that people worked with. */
val db11Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.TOOL,
        section = "Tables",
        title = "The WPA Mathematical Tables Project",
        originalTitle = "Mathematical Tables Project, a Works Progress Administration project in New York (1938)",
        author = "Arnold Lowan and Gertrude Blanch",
        year = 1938,
        summary = "A project that employed out-of-work people to calculate tables of mathematical functions by hand, for use in science and engineering.",
        details = "The project began in January 1938 in New York City, under Arnold Lowan, with Gertrude Blanch leading the mathematics. The Works Progress Administration ended its sponsorship in 1943, and the group carried on in New York until 1948, when about 25 of its staff moved to Washington and formed the computation laboratory of the National Bureau of Standards. Wikipedia calls the Handbook of Mathematical Functions, published in 1964, the project's greatest legacy.",
        rare = true,
    ),

    entry(
        databaseId = 11,
        kind = Kind.TOOL,
        section = "Instruments",
        title = "The HP 200A audio oscillator",
        originalTitle = "Model 200A audio oscillator, the first product of Hewlett-Packard (1939)",
        author = "William Hewlett and David Packard",
        year = 1939,
        summary = "An audio oscillator sold by a new company in Palo Alto, which became the first product of Hewlett-Packard.",
        details = "The company was founded on 1 January 1939, and the 200A is recorded as its first product. Walt Disney's studio bought several units to check the sound chain for Fantasia. Accounts differ on the number, eight or nine.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.TOOL,
        section = "Tables",
        title = "A table of 100,000 random digits, the 1939 tract",
        originalTitle = "Tables of random sampling numbers (Kendall and Babington Smith, 1939)",
        author = "Maurice Kendall and Bernard Babington Smith",
        year = 1939,
        summary = "A table of 100,000 random digits, made by a machine with a human operator and issued as one of the Tracts for Computers.",
        details = "The tract appeared at Cambridge in 1939 as number 24 of the Tracts for Computers. The authors had set out the statistical checks on the digits in a 1938 paper. The table stayed in common use until the RAND table of a million random digits appeared in 1955.",
        rare = true,
    ),

    entry(
        databaseId = 11,
        kind = Kind.TOOL,
        section = "Mathematical literature",
        title = "Zentralblatt fuer Mathematik, the reviewing journal of 1931",
        originalTitle = "Zentralblatt fuer Mathematik und ihre Grenzgebiete (1931)",
        author = "Richard Courant, Otto Neugebauer, Harald Bohr and Ferdinand Springer",
        year = 1931,
        summary = "A reviewing journal that gave short reviews of mathematical papers and books, so that readers could find work in a field quickly.",
        details = "The first volume appeared in 1931 from the publisher Springer, with its editorial base in Berlin. It was Germany's second comprehensive review journal, after the Jahrbuch ueber die Fortschritte der Mathematik of 1868. Political pressure after 1933 hit its work, and it later became the database zbMATH.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.TOOL,
        section = "Mathematical literature",
        title = "Mathematical Reviews, the American reviewing journal",
        originalTitle = "Mathematical Reviews, first issue dated January 1940",
        author = "American Mathematical Society",
        year = 1940,
        summary = "A reviewing journal founded by the American Mathematical Society, started when restrictions on reviewing in Germany led American mathematicians to build their own.",
        details = "The first issue, dated January 1940, carried 176 reviews on 32 pages. The society's history names Otto Neugebauer as its first editor, although a notice before the launch also named J. D. Tamarkin. The journal grew into the MathSciNet database.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.TOOL,
        section = "Data entry",
        title = "The IBM 032 printing punch",
        originalTitle = "IBM Type 032 printing punch (1933)",
        author = "International Business Machines",
        year = 1933,
        summary = "A keypunch that printed each character across the top edge of the card as the key was pressed, so that a card could be read by people as well as by machines.",
        details = "Columbia University's computing history credits the 032 as an early machine that punched letters as well as digits. Another reference dates an IBM alphabetic printing punch, the 036, to 1930, so the claim of priority for the 032 is uncertain.",
    ),
)
