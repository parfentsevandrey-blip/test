package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 395: a count of the thirty lives of the Historia Augusta, with the groups of rulers counted apart. */
val db165Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.EXAMPLE,
        section = "Lives",
        title = "The thirty lives of the Historia Augusta, counted in code",
        originalTitle = "Historia Augusta",
        author = "Six named writers, probably one author",
        year = 395,
        summary = "The Historia Augusta is a collection of thirty imperial lives from Hadrian to Numerian, and a short program counts the lives and the lives that cover a group of rulers.",
        details = "The six named authors are generally treated as fictional, and most scholars favour a single writer, but the date is disputed between the late fourth and the early fifth century. The count follows the usual numbering, in which groups such as the Two Maximini count as one life, and 395 is used here as a round date within that debate.",
        instructions = """
            1. Save the example as historia_augusta.py.
            2. Run it: python3 historia_augusta.py
        """,
        example = """
            lives = [
                "Hadrian", "Aelius", "Antoninus Pius", "Marcus Antoninus", "Lucius Verus",
                "Avidius Cassius", "Commodus", "Pertinax", "Didius Julianus", "Septimius Severus",
                "Pescennius Niger", "Clodius Albinus", "Antoninus Caracalla", "Geta", "Macrinus",
                "Diadumenianus", "Elagabalus", "Severus Alexander", "The Two Maximini", "The Three Gordians",
                "Maximus and Balbinus", "The Two Valerians", "The Two Gallieni", "The Thirty Tyrants",
                "Claudius", "Aurelian", "Tacitus", "Florian", "Probus", "Carus, Carinus and Numerian",
            ]
            groups = [name for name in lives if name.startswith("The ") or " and " in name]
            print("lives:", len(lives))
            print("lives that cover a group of rulers:", len(groups))
            print("single-ruler lives:", len(lives) - len(groups))
            print("first life:", lives[0])
            print("last life:", lives[-1])
        """,
        rare = true,
    ),

)
