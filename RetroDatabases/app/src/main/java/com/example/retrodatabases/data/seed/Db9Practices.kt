package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 2026 to 2036, as a forecast: inventories, access reviews, specs, local-first apps and carbon budgets. */
val db9Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.PRACTICE,
        section = "Security",
        title = "A crypto inventory and crypto agility",
        originalTitle = "NIST IR 8547, the draft transition to post-quantum standards",
        author = "NIST, draft",
        year = 2027,
        summary = "Forecast: every company keeps a list of the cryptographic algorithms in its systems and can replace them without a rewrite.",
        details = "Forecast. NIST's draft transition report, IR 8547, was first published in November 2024. It proposes deprecating RSA and elliptic-curve schemes after 2030 and disallowing them after 2035. The forecast is that teams keep an inventory of algorithms and certificates by 2027, because auditors will ask for it first. The draft was not final in mid-2026, so the year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.PRACTICE,
        section = "Agents",
        title = "Agent access reviews",
        originalTitle = "Access reviews, a control used for people's accounts",
        author = "Forecast written for this database",
        year = 2028,
        summary = "Forecast: teams review every quarter which tools each agent may call, the way they review user accounts today.",
        details = "Forecast. Access reviews are a standard control for people, and agents now hold credentials of their own. The forecast is that auditors ask for agent access reviews, with a log of every tool call, by 2028. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.PRACTICE,
        section = "Process",
        title = "Spec before code with agents",
        originalTitle = "Spec-first development",
        author = "Forecast written for this database",
        year = 2027,
        summary = "Forecast: a change starts with a written spec and its tests, and the agent is asked to meet them rather than guess at intent.",
        details = "Forecast. Teams that use coding agents already write a short spec before a task, because a vague request produces vague code. The forecast is that spec-first becomes the default, and that reviews check the spec and the tests before the code. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.PRACTICE,
        section = "Architecture",
        title = "Local-first software with offline sync",
        originalTitle = "Local-first software, an essay by Kleppmann and collaborators (2019)",
        author = "Martin Kleppmann and collaborators",
        year = 2030,
        summary = "Forecast: most new collaborative apps keep data on the device first and merge changes later, so they work without a central server.",
        details = "Forecast. The local-first essay of 2019 described apps that keep working offline and merge changes between devices, using CRDTs. The forecast is that by 2030 many collaborative tools work this way, because users expect them to work on a plane or with a weak signal. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.PRACTICE,
        section = "Green software",
        title = "Carbon budgets for software teams",
        originalTitle = "Corporate carbon reporting under the EU's CSRD",
        author = "Forecast written for this database",
        year = 2028,
        summary = "Forecast: a team has a yearly carbon budget for its services, and a release that uses more energy needs a change or an offset.",
        details = "Forecast. Large companies in the EU already report emissions under the Corporate Sustainability Reporting Directive, and cloud providers publish energy data per region. The forecast is that software teams get per-service budgets, reviewed like cost budgets. The year is a guess, and the method is not agreed yet.",
    ),
)
