package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1620 to 1630: a tied arm that showed the flow of blood, logarithm computing, teaching by hand letters and catechism, and abacus lessons. */
val db42Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.PRACTICE,
        section = "Experiment",
        title = "Harvey's ligature of the arm",
        originalTitle = "Exercitatio anatomica de motu cordis et sanguinis in animalibus",
        author = "William Harvey",
        year = 1628,
        summary = "Harvey tied a band round the upper arm to show that the veins swell on the far side of the band from the heart, which the valves in the veins explain.",
        details = "The experiment is described in the 1628 book, where a band restricts the flow in the veins of the arm. The swelling beyond the band, together with the valves that open only toward the heart, is the basis of Harvey's claim that the blood flows one way.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.PRACTICE,
        section = "Computing",
        title = "Computing with logarithm tables",
        originalTitle = "Arithmetica logarithmica",
        author = "Henry Briggs",
        year = 1624,
        summary = "From the 1620s, astronomers and surveyors turned products and quotients into sums and differences of logarithms, which cut the labour of long multiplication.",
        details = "The method spread with Briggs's tables of 1624 and was used for the planetary tables in Kepler's Rudolphine. Accounts of how much labour it saved vary, but the method is the practice itself, and the tables are the tools that carried it.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "Teaching deaf pupils by hand letters, then speech",
        originalTitle = "Reduccion de las letras y arte para ensenar a ablar los mudos",
        author = "Juan Pablo Bonet",
        year = 1620,
        summary = "Bonet's method taught deaf pupils the letters first by hand and then the sounds of speech, a method set out in his Madrid treatise of 1620.",
        details = "The method is described in the 1620 treatise, which moves from the manual letters to the sounds of speech. The hand alphabet is usually traced to Pedro Ponce de Leon, so the practice is recorded as the method that the treatise describes, not as Bonet's sole invention.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "Catechism taught in the vernacular for the Kongo mission",
        originalTitle = "Doutrina Christam",
        author = "Mattheus Cardoso",
        year = 1624,
        summary = "The Kongo mission taught the catechism in Kikongo so that converts could learn the faith in their own language, a practice that Cardoso's printed book of 1624 made possible.",
        details = "The 1624 book was printed in Lisbon for the Kongo mission and is the printed form of this teaching. The practice of teaching in the vernacular is recorded here through the book itself, and the date is the printing date of 1624.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.PRACTICE,
        section = "Arithmetic",
        title = "Calculating with the abacus from worked problems",
        originalTitle = "Jinkoki",
        author = "Yoshida Mitsuyoshi",
        year = 1627,
        summary = "Yoshida's book taught the abacus through worked problems, so that calculation was learned by method and repetition, a practice that spread through Japanese schools.",
        details = "The book of 1627 sets out problems and their solutions in the order of a learner's course. The method was taken up widely in Japan in the centuries after, and the practice is recorded here through the book's method.",
    ),

)
