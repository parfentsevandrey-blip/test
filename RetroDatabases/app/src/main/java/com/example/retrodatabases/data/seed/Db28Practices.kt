package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1760 to 1770: age-based premiums, a smallpox life-table estimate, transit observations and ship classing. */
val db28Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.PRACTICE,
        section = "Insurance",
        title = "Age-based premiums of the Equitable Society, 1762",
        originalTitle = "Age-based premiums of the Equitable Society, 1762",
        author = "The Society for Equitable Assurances on Lives and Survivorships",
        year = 1762,
        summary = "A life insurer that set premiums by the age of the policyholder, using a mortality table with 3 per cent interest assumed.",
        details = "The Society for Equitable Assurances on Lives and Survivorships was founded in London in 1762, and it charged premiums that rose with age. The premiums were worked out from a mortality table with 3 per cent interest assumed, and accounts differ on whether that table came from Halley's earlier work or from London death records. Later experience showed that survival was better than the table assumed.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Bernoulli's life-table estimate for smallpox inoculation",
        originalTitle = "Essai d'une nouvelle analyse de la mortalite causee par la petite verole",
        author = "Daniel Bernoulli",
        year = 1760,
        summary = "A calculation from life tables that estimated how much inoculation against smallpox would lengthen the average life.",
        details = "Bernoulli read his memoir to the Paris Academy in 1760, and it was printed in 1766, so accounts differ on the date. He used Halley's life tables to estimate the expectation of life, and he judged that removing smallpox would lengthen it by about three years. He also reckoned that inoculation was worth doing while its fatality stayed under about one in ten.",
        rare = true,
    ),

    entry(
        databaseId = 28,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Halley's method for the Sun's distance, used in 1761",
        originalTitle = "A new method of determining the parallax of the sun, from the transits of Venus",
        author = "Edmond Halley",
        year = 1761,
        summary = "A method for finding the distance of the Sun by timing the passage of Venus across its disc from different places on Earth.",
        details = "Halley proposed the method in 1716, and observers in the 1761 transit of Venus used it from stations across the world. They timed the contacts of Venus with the Sun's edge, and differences between stations gave the solar parallax. Weather and poor timing spoiled many of the 1761 observations, so the results were disputed.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Lomonosov's sighting of an atmosphere on Venus",
        originalTitle = "Lomonosov's sighting of an atmosphere on Venus",
        author = "Mikhail Lomonosov",
        year = 1761,
        summary = "A report that Venus showed a ring of light against the Sun in 1761, which Lomonosov took as a sign of an atmosphere.",
        details = "Lomonosov watched the transit of Venus at St Petersburg in 1761 and reported a ring of light around the planet's dark disc, which he took as a sign of an atmosphere. His account was published in 1762. Later observers did not agree that the ring was a real effect, so the claim remains disputed.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.PRACTICE,
        section = "Shipping",
        title = "Lloyd's surveyors and the ship classes of 1764",
        originalTitle = "Lloyd's Register of Shipping",
        author = "Lloyd's Coffee House, London",
        year = 1764,
        summary = "A register that classed ships by the surveyed condition of their hulls, so that merchants and underwriters could judge the risk of a voyage.",
        details = "Lloyd's Coffee House in London began to publish a register of ships in 1764, with a class for each ship's build and condition. Underwriters and merchants used the classes to judge the risk of insuring a voyage. The method of surveying was refined in the decades that followed.",
    ),
)
