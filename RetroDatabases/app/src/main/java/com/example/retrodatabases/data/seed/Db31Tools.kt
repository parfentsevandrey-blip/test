package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1730 to 1740: a fluid treatise, a lecture course, an optics survey, a chemistry text, a tuning treatise and a hydraulic handbook. */
val db31Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.TOOL,
        section = "Fluids",
        title = "Bernoulli's Hydrodynamica",
        originalTitle = "Hydrodynamica, sive de viribus et motibus fluidorum commentarii",
        author = "Daniel Bernoulli",
        year = 1738,
        summary = "A treatise on the forces and motions of fluids, which contains the balance of pressure and speed along a stream that carries Bernoulli's name.",
        details = "Daniel Bernoulli printed the Hydrodynamica at Strasbourg in 1738. It treats the flow of water from vessels and through pipes, and its balance of pressure and speed is now called Bernoulli's principle.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.TOOL,
        section = "Teaching",
        title = "Desaguliers's Course of Experimental Philosophy",
        originalTitle = "A Course of Experimental Philosophy",
        author = "John Theophilus Desaguliers",
        year = 1734,
        summary = "A course of lectures built on demonstrations with apparatus in mechanics, hydrostatics and optics, written as a text for audiences.",
        details = "The first volume of the course appeared in London in 1734, and a second followed in 1744. It described experiments that a lecturer could show with apparatus, and it helped to spread Newtonian natural philosophy.",
        rare = true,
    ),

    entry(
        databaseId = 31,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Smith's Compleat System of Opticks",
        originalTitle = "A Compleat System of Opticks",
        author = "Robert Smith",
        year = 1738,
        summary = "A survey of optics in two volumes that gathers the theory of light, vision and optical instruments for students.",
        details = "Robert Smith, a Cambridge mathematician, published the book in 1738. It covers the theory of lenses, the eye and optical instruments, and it was read as a student text.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.TOOL,
        section = "Chemistry",
        title = "Boerhaave's Elementa chemiae",
        originalTitle = "Elementa chemiae",
        author = "Hermann Boerhaave",
        year = 1732,
        summary = "A two-volume chemistry text that organises the subject around the operations of the laboratory, such as distillation and sublimation.",
        details = "Boerhaave printed the Elementa chemiae at Leiden in 1732. It arranged chemistry around the processes of the laboratory, and it was one of the standard chemistry texts of the century.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.TOOL,
        section = "Tuning",
        title = "Neidhardt's temperament treatise of 1732",
        originalTitle = "Gaenzlich erschoepfte mathematische Abtheilungen des diatonisch-chromatischen, temperirten Canonis Monochordi",
        author = "Johann Georg Neidhardt",
        year = 1732,
        summary = "A treatise that works out the tuning systems for keyboard instruments and gives the numbers for each, aimed at amateur tuners.",
        details = "Neidhardt published the treatise in Konigsberg in 1732. It teaches the reader to derive each tuning system and to express it numerically.",
        rare = true,
    ),

    entry(
        databaseId = 31,
        kind = Kind.TOOL,
        section = "Hydraulics",
        title = "Belidor's Architecture hydraulique",
        originalTitle = "Architecture hydraulique, ou l'art de conduire, d'elever et de menager les eaux",
        author = "Bernard Forest de Belidor",
        year = 1737,
        summary = "A handbook for engineers on conducting, raising and managing water, issued in several volumes from 1737.",
        details = "The first volume appeared in Paris in 1737, and the later volumes followed in 1739, 1750 and 1753. It covers pumps, water wheels, canals and the pressure of water in pipes.",
    ),
)
