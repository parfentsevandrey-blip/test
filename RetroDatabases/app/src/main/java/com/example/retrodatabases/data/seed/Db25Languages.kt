package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1790 to 1800: notations for functions, projections and derivations, metric and calendar names, and a decree in three scripts. */
val db25Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.LANGUAGE,
        section = "Calculus notation",
        title = "Lagrange's functional notation f(x)",
        originalTitle = "Theorie des fonctions analytiques (1797)",
        author = "Joseph-Louis Lagrange",
        year = 1797,
        summary = "A function is written as f(x), and its derived function f'(x) is defined from the power series of f.",
        details = "In the 1797 book Lagrange set out the notation f(x) and the derived function f'(x), and he tried to found the calculus on power series without limits. That foundation did not last, since Cauchy later rebuilt the subject on limits, but the notation stayed in use.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.LANGUAGE,
        section = "Projection notation",
        title = "Monge's descriptive geometry",
        originalTitle = "Geometrie descriptive (1799)",
        author = "Gaspard Monge",
        year = 1799,
        summary = "A solid is drawn as two projections on two planes, a horizontal plan and a vertical elevation, so that a flat sheet shows its shape exactly.",
        details = "Monge taught the method at the Ecole normale in 1795, and the lectures were printed in 1799. A point is fixed by its two projections, and the vertical plane is folded onto the horizontal one to make a single sheet. The method became the basis of technical drawing in France.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.LANGUAGE,
        section = "Derivation calculus",
        title = "Arbogast's calculus of derivations",
        originalTitle = "Du calcul des derivations (1800)",
        author = "Louis Francois Antoine Arbogast",
        year = 1800,
        summary = "Arbogast treated differentiation as an operation with its own symbol D, and gave rules for adding and multiplying such operations.",
        details = "The 1800 book set out the rules for combining derivation operators, so that equations between operators could be handled like equations between numbers. Later algebraists kept the symbol D for differentiation and built operator calculus on it.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.LANGUAGE,
        section = "Metric names",
        title = "Metric prefixes from deca to myria",
        originalTitle = "Loi du 18 germinal an III (1795)",
        author = "French National Convention",
        year = 1795,
        summary = "The decimal prefixes deca, hecto, kilo and myria multiply the unit by ten, one hundred, one thousand and ten thousand, and deci, centi and milli divide it.",
        details = "The law of 18 germinal year III, dated 7 April 1795, fixed these prefixes for the metre and the gram. Myria stood for ten thousand and did not last, while kilo, hecto and deca stayed. The same prefixes now name multiples and parts of the SI units, apart from myria.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.LANGUAGE,
        section = "Calendar names",
        title = "Republican names of the months",
        originalTitle = "Decree naming the months of the Republican calendar (1793)",
        author = "Fabre d'Eglantine and the National Convention",
        year = 1793,
        summary = "Twelve months took names from nature and the seasons, such as Vendemiaire for the vintage, in place of the Gregorian names.",
        details = "The Convention adopted the names in the autumn of 1793 for a calendar that counted years from the proclamation of the Republic. The poet Fabre d'Eglantine is credited with the names, and each month had three ten-day decades.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.LANGUAGE,
        section = "Writing systems",
        title = "The Rosetta Stone and its three scripts",
        originalTitle = "Decree of Memphis, carved on the Rosetta Stone (196 BC)",
        author = "Priests of Memphis",
        year = 1799,
        summary = "A decree of 196 BC carved in hieroglyphic, demotic and Greek script, which became the key to reading the hieroglyphs.",
        details = "French soldiers found the stone near Rosetta in July 1799, during Napoleon's expedition to Egypt. The Greek text could already be read, and it gave the key to the hieroglyphic and demotic passages.",
    ),
)
