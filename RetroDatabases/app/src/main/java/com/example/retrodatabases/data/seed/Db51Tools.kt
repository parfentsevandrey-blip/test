package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1530 to 1540: a heart-shaped world map, Agrippa's book of planetary squares, Nunes's rhumb-line treatise, Vesalius's anatomical tables, Cardano's arithmetic and Brunfels's herbal. */
val db51Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Fine's heart-shaped map of the world",
        originalTitle = "Nova et integra universi orbis descriptio",
        author = "Oronce Fine",
        year = 1531,
        summary = "Fine's heart-shaped map of 1531 shows the whole world in two hemispheres inside a heart-shaped outline, a projection that many later maps copied.",
        details = "The map was printed in Paris in 1531 and is among the best-known heart-shaped world maps of the sixteenth century. Its outline was chosen to hold the two hemispheres on one sheet, and the shape was discussed by mapmakers for decades afterwards.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.TOOL,
        section = "Magic",
        title = "Agrippa's Three Books of Occult Philosophy",
        originalTitle = "De occulta philosophia libri tres",
        author = "Cornelius Agrippa",
        year = 1533,
        summary = "Agrippa's Three Books of Occult Philosophy, first published in 1533, gives a magic square for each of the seven planets, each square tied to its planet.",
        details = "The Linda Hall Library dates the first publication to 1533, and that is the date given here. The squares are assigned to Saturn, Jupiter, Mars, the Sun, Venus, Mercury and the Moon, and the book was widely read in the centuries that followed.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Nunes's treatise on the rhumb line of sea charts",
        originalTitle = "Tratado em defensam da carta de marear",
        author = "Pedro Nunes",
        year = 1537,
        summary = "Nunes's treatise of 1537 studied the rhumb line, the course that keeps a fixed angle with every meridian, and it is an early study of the curve that this course makes on the sphere.",
        details = "The treatise was printed at Lisbon in 1537, in Portuguese, and it defends the sea chart of its time. The curve of the rhumb on the globe is the point that later mathematicians examined most closely.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.TOOL,
        section = "Anatomy",
        title = "Vesalius's six anatomical tables",
        originalTitle = "Tabulae anatomicae sex",
        author = "Andreas Vesalius",
        year = 1538,
        summary = "Vesalius's six anatomical tables of 1538 were large woodcut plates of the human body, issued as a set to show the parts of the body in detail.",
        details = "The tables were printed at Venice in 1538, and they were followed in 1543 by Vesalius's larger work, the Fabrica. The plates were made to show the parts of the body for teaching, and they became a model for anatomical illustration.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Cardano's practical arithmetic of 1539",
        originalTitle = "Practica arithmeticae et mensurandi singularis",
        author = "Girolamo Cardano",
        year = 1539,
        summary = "Cardano's Practica arithmeticae of 1539 is a practical book of arithmetic and measurement for merchants and surveyors, with worked rules for calculation.",
        details = "The book was printed at Milan in 1539, and it belongs to the Italian tradition of practical arithmetic that merchants used. It came before Cardano's Ars magna of 1545.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.TOOL,
        section = "Botany",
        title = "Brunfels's herbal of living pictures",
        originalTitle = "Herbarum vivae eicones",
        author = "Otto Brunfels",
        year = 1530,
        summary = "Brunfels's herbal of 1530 printed plants with woodcut pictures drawn from life, and its illustrations were made by Hans Weiditz.",
        details = "The first volume was printed at Strasbourg in 1530, and its woodcuts show plants with close attention to their leaves and flowers.",
        rare = true,
    ),

)
