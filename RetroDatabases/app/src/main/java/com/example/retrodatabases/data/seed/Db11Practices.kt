package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1930 to 1940: the methods for designing circuits, judging quality, testing and planning that the decade set down. */
val db11Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.PRACTICE,
        section = "Logic design",
        title = "Shannon's switching algebra for relay circuits",
        originalTitle = "A symbolic analysis of relay and switching circuits (Shannon, 1937)",
        author = "Claude Shannon",
        year = 1937,
        summary = "Series and parallel contacts correspond to the AND and OR of Boolean algebra, so a circuit can be designed and simplified on paper.",
        details = "Shannon's master's thesis at MIT showed how the behaviour of relay networks can be written as Boolean expressions, and how a network can be designed for a given behaviour. The thesis was completed in 1937 and published in 1938. Shannon treated adders with the same method, and the method became the basis of digital circuit design.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.PRACTICE,
        section = "Quality",
        title = "Shewhart control charts for a manufacturing process",
        originalTitle = "Economic control of quality of manufactured product (Shewhart, 1931)",
        author = "Walter Shewhart",
        year = 1931,
        summary = "A control chart plots measurements in time order between limits set three standard deviations from the mean, so a point outside the limits signals a cause to investigate.",
        details = "Shewhart proposed the chart in a 1924 internal memorandum at Bell Telephone Laboratories, and set out the method in his 1931 book. Points inside the limits count as normal variation, and points outside them trigger an investigation.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.PRACTICE,
        section = "Statistics",
        title = "Confidence intervals, from Neyman's 1937 theory",
        originalTitle = "Outline of a theory of statistical estimation based on the classical theory of probability (Neyman, 1937)",
        author = "Jerzy Neyman",
        year = 1937,
        summary = "A confidence interval comes from a method that covers the true value in a fixed fraction of repeated samples, so the guarantee belongs to the method rather than to one interval.",
        details = "Neyman's 1937 paper, printed in the Philosophical Transactions of the Royal Society, set out the theory of interval estimation and the idea of coverage. A procedure with a 95 percent confidence level covers the true value in 95 of every 100 repeated samples.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.PRACTICE,
        section = "Probability",
        title = "Kolmogorov's axioms of probability",
        originalTitle = "Grundbegriffe der Wahrscheinlichkeitsrechnung (Kolmogorov, 1933)",
        author = "Andrei Kolmogorov",
        year = 1933,
        summary = "Probability is a measure on a family of events, fixed by three axioms from which the rest of probability theory follows.",
        details = "Kolmogorov's monograph, published in Berlin in 1933, put probability on a measure-theoretic footing. The three axioms require that probabilities are non-negative, that the probability of the whole space is one, and that the probabilities of disjoint events add.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.PRACTICE,
        section = "Economics",
        title = "Input and output tables, Leontief's model of 1936",
        originalTitle = "Quantitative input and output relations in the economic system of the United States (Leontief, 1936)",
        author = "Wassily Leontief",
        year = 1936,
        summary = "A table of what each industry buys from and sells to the others, solved as a system of linear equations, gives the output needed to meet a final demand.",
        details = "Leontief's 1936 paper gave the first input-output table of the United States economy. Solving the system for a given final demand is a set of linear equations, which in the 1930s had to be done by hand or with desk calculators.",
    ),
)
