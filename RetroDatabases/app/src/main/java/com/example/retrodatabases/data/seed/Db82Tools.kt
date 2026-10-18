package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1220 to 1225: Sacrobosco's textbook of the sphere, Fibonacci's book of squares and his Flos, Caesarius's tales for preachers and Grosseteste's treatise on light. */
val db82Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Sacrobosco's De sphaera, the university textbook of the sphere",
        originalTitle = "Tractatus de sphaera",
        author = "Johannes de Sacrobosco",
        year = 1220,
        summary = "Sacrobosco's De sphaera is a short Latin textbook of the celestial sphere in four chapters, and it was the standard introduction to astronomy in European universities for centuries.",
        details = "Its date is uncertain, and most accounts place it about 1220 to 1230, while the one firm date for the author is his appointment as a teacher at Paris in June 1221. The book came into wide use across Europe from about the 1250s and stayed a standard university text into the 1600s.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.TOOL,
        section = "Number theory",
        title = "Fibonacci's Liber quadratorum, the Book of Squares written for Frederick II in 1225",
        originalTitle = "Liber quadratorum",
        author = "Leonardo Fibonacci of Pisa",
        year = 1225,
        summary = "The Liber quadratorum is Fibonacci's number book of 1225 on squares, and its best-known exercise asks for a square whose values five units above and below are both squares.",
        details = "The book defines a congruum as a number n such that some square has squares both n above and n below it, and it notes that no congruum can itself be a square. Fibonacci wrote it for Emperor Frederick II, whose court at Pisa had invited him, and the problem of five became a classic of number theory.",
        rare = true,
    ),

    entry(
        databaseId = 82,
        kind = Kind.TOOL,
        section = "Preaching",
        title = "Caesarius of Heisterbach's Dialogue on Miracles, a store of tales for preachers",
        originalTitle = "Dialogus miraculorum",
        author = "Caesarius of Heisterbach",
        year = 1223,
        summary = "Caesarius of Heisterbach's Dialogue on Miracles is a Latin collection of stories and exempla, written by a Cistercian monk as a dialogue between a master and a novice.",
        details = "He wrote it in the years from about 1219 to 1223 at the Cistercian abbey of Heisterbach near Bonn, and its tales were copied widely and used by preachers for sermons. The completion is usually dated 1223.",
        rare = true,
    ),

    entry(
        databaseId = 82,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Fibonacci's Flos, a Latin book of 1225 on a cubic and other challenges",
        originalTitle = "Flos",
        author = "Leonardo Fibonacci of Pisa",
        year = 1225,
        summary = "Fibonacci's Flos is a short Latin book of 1225 that answers three problems set by John of Palermo, including a cubic equation whose root Fibonacci gave in base sixty.",
        details = "The cubic is x cubed plus two x squared plus ten x equals twenty, and the root is given as 1;22,7,42,33,4 in sexagesimal fractions, a value accurate to about nine decimal places. The book was written after the challenges at the court of Frederick II, and it argues that the root is not one of the irrational lines that Euclid described.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Grosseteste's De luce, a treatise on light as the first form of bodies",
        originalTitle = "De luce",
        author = "Robert Grosseteste",
        year = 1220,
        summary = "Robert Grosseteste's De luce is a short Latin treatise that takes light to be the first form of bodies and builds a cosmology in which light spreads to form the heavens.",
        details = "Grosseteste was then teaching at Oxford, and the treatise is usually dated about 1220, though some scholars place it in the mid-1220s. Its reasoning runs in steps from light, a quality that spreads out in every direction, to the spheres of the universe.",
        rare = true,
    ),

)
