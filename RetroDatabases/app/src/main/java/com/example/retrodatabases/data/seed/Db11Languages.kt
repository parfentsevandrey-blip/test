package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1930 to 1940: the formal notations that the decade invented for computation and proof. */
val db11Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.LANGUAGE,
        section = "Functions",
        title = "Lambda calculus, a notation in which everything is a function",
        originalTitle = "An unsolvable problem of elementary number theory (Church, 1936)",
        author = "Alonzo Church",
        year = 1936,
        summary = "Church's notation writes every function as a lambda term, with only abstraction and application, and it can encode numbers and logic.",
        details = "Church first published the calculus in 1932. In 1936 he used it to show that the decision problem of first-order logic has no general solution. Terms are built from variables, abstraction and application, and reduction rules change one term into another. Numbers can be encoded as repeated application of a function.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.LANGUAGE,
        section = "Machines",
        title = "Turing's tables of states and symbols",
        originalTitle = "On computable numbers, with an application to the Entscheidungsproblem (Turing, 1936)",
        author = "Alan Turing",
        year = 1936,
        summary = "A machine is described by a finite table: in each state it reads a symbol, writes one, moves the head and changes state.",
        details = "The paper was received in May 1936, read in November and printed in 1937. Each machine is a finite table of instructions, and a single universal machine can carry out the work of any such table. Turing used the construction to show that the decision problem has no general solution.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.LANGUAGE,
        section = "Formal systems",
        title = "Post's finite process, a worker on a row of boxes",
        originalTitle = "Finite combinatory processes, formulation I (Post, 1936)",
        author = "Emil Post",
        year = 1936,
        summary = "Post described a finite process as instructions for a worker who moves along a row of boxes, marking and erasing as it goes.",
        details = "The note appeared in the Journal of Symbolic Logic in September 1936, on three pages. It argued that every definite method can be written as such a worker, a step that Church's lambda formulation had not established. The worker can mark or erase the box it stands on, move one box left or right, branch on whether the box is marked, and stop.",
        rare = true,
    ),

    entry(
        databaseId = 11,
        kind = Kind.LANGUAGE,
        section = "Proofs",
        title = "Gentzen's sequent calculus",
        originalTitle = "Untersuchungen ueber das logische Schliessen (Gentzen, 1934)",
        author = "Gerhard Gentzen",
        year = 1934,
        summary = "Gentzen wrote logical proofs as sequences of sequents with one rule for each logical symbol, which made proofs easy to check by a fixed procedure.",
        details = "The paper appeared in Mathematische Zeitschrift in 1934 and 1935. It introduced natural deduction and the sequent calculus, and it proved the cut-elimination theorem, which says that a proof can be found without the cut rule.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "Tarski's formalised languages and their truth",
        originalTitle = "The concept of truth in formalized languages (Tarski, 1933)",
        author = "Alfred Tarski",
        year = 1933,
        summary = "Tarski separated the object language from the metalanguage and defined truth for a formal language by recursion on the structure of its sentences.",
        details = "The Polish monograph appeared in 1933, and a German version followed in 1935. The distinction between an object language and the language used to talk about it is still the basis of formal logic and of the design of formal languages.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "The formal calculus of Hilbert and Bernays",
        originalTitle = "Grundlagen der Mathematik, volume 1 (Hilbert and Bernays, 1934)",
        author = "David Hilbert and Paul Bernays",
        year = 1934,
        summary = "The Grundlagen gives a formal language and rules for derivation in full, so each proof can be checked step by step by a finite procedure.",
        details = "The first volume appeared in 1934 and the second in 1939. It sets out the formal language of first-order logic and the rules for deriving formulas in it, which later writers used as the reference for checking a proof mechanically.",
        rare = true,
    ),
)
