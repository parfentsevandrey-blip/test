package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1660 to 1670: a microscope observation of blood vessels, a test of light at shadows, a controlled test on insects, early transfusions and a double refraction. */
val db38Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.PRACTICE,
        section = "Anatomy",
        title = "Malpighi's capillaries in a frog's lung",
        originalTitle = "De pulmonibus",
        author = "Marcello Malpighi",
        year = 1661,
        summary = "In 1661 Malpighi saw blood moving through the tiny vessels of a frog's lung, the link between arteries and veins that Harvey had argued for without seeing.",
        details = "Malpighi reported the observation in his letters on the lungs of 1661, after examining a thin piece of lung under a simple microscope. Harvey had inferred such vessels from the circulation of the blood, and Malpighi's sight of them filled a gap in the theory.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.PRACTICE,
        section = "Optics",
        title = "Grimaldi's shadows of a thin beam of light",
        originalTitle = "Physico-mathesis de lumine, coloribus, et iride",
        author = "Francesco Maria Grimaldi",
        year = 1665,
        summary = "Grimaldi saw that light passing a thin rod or a narrow opening spread into the shadow, and he gave the spreading the name diffraction, a word still in use.",
        details = "His book was printed at Bologna in 1665, after his death in 1663, and it reported the coloured bands he saw at the edges of shadows. The explanation of the bands waited for the wave theory of light, but the word he chose has kept its place in physics.",
        rare = true,
    ),

    entry(
        databaseId = 38,
        kind = Kind.PRACTICE,
        section = "Biology",
        title = "Redi's netted jars against spontaneous generation",
        originalTitle = "Esperienze intorno alla generazione degl'insetti",
        author = "Francesco Redi",
        year = 1668,
        summary = "Redi covered meat in jars with gauze and showed that maggots did not appear in meat that flies could not reach, a controlled test of spontaneous generation.",
        details = "His book of 1668 reported jars with and without gauze covers, and it is often cited as an early controlled experiment. Redi did not settle the wider question of whether all life comes from parents, and accounts differ on how far his results were meant to answer it.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.PRACTICE,
        section = "Medicine",
        title = "Blood transfusions by Lower and by Denis",
        originalTitle = "Transfusion of blood",
        author = "Richard Lower and Jean-Baptiste Denis",
        year = 1666,
        summary = "In 1666 Lower and King passed blood between dogs in London, and in 1667 Denis passed animal blood into people in Paris.",
        details = "The London trials of 1666 and the Paris trials of 1667 were among the first attempts to move blood from one animal into another or into a person. The early trials were few, and accounts differ on how many patients received blood and what happened to them.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.PRACTICE,
        section = "Optics",
        title = "Bartholin's double refraction in Iceland spar",
        originalTitle = "Experimenta crystalli Islandici disdiaclastici",
        author = "Erasmus Bartholin",
        year = 1669,
        summary = "Erasmus Bartholin reported that a crystal of Iceland spar splits a ray of light into two, the effect later called double refraction.",
        details = "He printed the observations at Copenhagen in 1669, and the title he chose uses the word disdiaclastic for the splitting. Christiaan Huygens explained the two rays in his Traite de la lumiere of 1690, using his wave theory of light.",
    ),

)
