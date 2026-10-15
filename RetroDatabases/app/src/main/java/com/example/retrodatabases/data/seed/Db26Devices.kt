package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1780 to 1790: a feedback governor, a difference engine on paper, a torsion balance and other instruments. */
val db26Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Steam engines",
        title = "Watt's centrifugal governor",
        originalTitle = "Governor for steam engines",
        author = "James Watt",
        year = 1788,
        summary = "Two weights on arms that rise with speed and close the steam valve, so that the engine keeps a steady speed.",
        details = "Watt patented the governor in 1788 and fitted it to his engines. It is a feedback device: the measured speed controls the supply of steam, and it is often named as an early automatic control.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Calculating",
        title = "Muller's difference engine design",
        originalTitle = "Difference engine, design of 1786",
        author = "Johann Helfrich Muller",
        year = 1786,
        summary = "A design for a machine that evaluates polynomials by the method of differences and would print the tables.",
        details = "Muller, an army engineer, described the principle in a book of 1786. The design needed funding that never came, so the machine was never built.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "Coulomb's torsion balance",
        originalTitle = "Balance de torsion",
        author = "Charles-Augustin de Coulomb",
        year = 1785,
        summary = "A thin fibre that twists under a small force, used to measure the force between charged bodies.",
        details = "Coulomb used the balance in 1785 to show that the electric force falls off with the square of the distance. The same kind of fibre was later used to measure small magnetic forces.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Copying",
        title = "Watt's letter-copying press",
        originalTitle = "Copying press, patent of 1780",
        author = "James Watt",
        year = 1780,
        summary = "A press that copied a handwritten letter by pressing it against damp paper, so that an office kept a copy of every letter.",
        details = "Watt patented the press in 1780 and sold it widely. Offices used it for more than a century, and it was among the first everyday devices that copied written text.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Herschel's 40-foot telescope",
        originalTitle = "40-foot reflector",
        author = "William Herschel",
        year = 1789,
        summary = "A large reflecting telescope with which Herschel found two new moons of Saturn.",
        details = "The telescope was finished in 1789. With it Herschel found Enceladus and Mimas, two moons of Saturn. The instrument was large and hard to handle, so it was used only on the clearest nights.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Security",
        title = "Bramah's lock",
        originalTitle = "Bramah lock, patent of 1784",
        author = "Joseph Bramah",
        year = 1784,
        summary = "A lock whose key has to be cut to a pattern of slots, so that the number of possible keys is very large.",
        details = "Bramah patented the lock in 1784 and offered a prize to anyone who could pick it. It was not picked until 1851, which made it a mechanical example of a secret code.",
        rare = true,
    ),

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Physiology",
        title = "Galvani's frog preparation",
        originalTitle = "De viribus electricitatis in motu musculari commentarius",
        author = "Luigi Galvani",
        year = 1786,
        summary = "A frog's leg hung on a copper hook that twitched when touched by a second metal.",
        details = "Galvani's observations in the 1780s led to the idea of electricity produced within animal tissue. Volta's disagreement with Galvani led to the electric pile of 1800.",
    ),

    entry(
        databaseId = 26,
        kind = Kind.DEVICE,
        section = "Lighting",
        title = "The Argand lamp",
        originalTitle = "Lampe d'Argand",
        author = "Aime Argand",
        year = 1780,
        summary = "A lamp with a hollow circular wick and a glass chimney, which gave a brighter and steadier flame than a candle.",
        details = "Argand patented the lamp in 1780. The air flowing through the wick kept the flame burning cleanly, and the lamp was quickly copied in many countries.",
    ),
)
