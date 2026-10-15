package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 2026 to 2036, as a forecast: each one is a guess, with the basis it rests on. */
val db9Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.LANGUAGE,
        section = "C++ successors",
        title = "Carbon",
        originalTitle = "Carbon Language, an experimental successor to C++",
        author = "Google, an experimental project",
        year = 2030,
        summary = "Forecast: Carbon, the experimental C++ successor from Google, gets a 1.0 release and is used next to C++ code through interop.",
        details = "Forecast. Carbon is still marked experimental, and its roadmap, in a copy that may be out of date, put the 0.1 milestone in late 2026. The forecast is that a 1.0 release comes around 2030 and that teams write new code in Carbon where they need memory safety and C++ interop. The year is a guess, and the project may also be stopped.",
        rare = true,
    ),

    entry(
        databaseId = 9,
        kind = Kind.LANGUAGE,
        section = "C++",
        title = "Static reflection in C++",
        originalTitle = "Reflection for C++26 (paper P2996)",
        author = "WG21, the ISO C++ committee",
        year = 2027,
        summary = "Forecast: C++ compilers that support C++26 reflect on types at compile time, so serializers and bindings no longer need a separate code generator.",
        details = "Forecast. The reflection proposal P2996 reached its thirteenth revision in June 2025, addressed to the core and library wording groups, and implementations existed in an EDG front end and in Bloomberg's Clang fork. The forecast is that mainstream compilers ship it in 2027, and that generators for serialization and bindings shrink. The year is a guess, and the wording may still change.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.LANGUAGE,
        section = "Java",
        title = "Value classes in Java",
        originalTitle = "Project Valhalla, JEP 401 (value classes and objects)",
        author = "OpenJDK Project Valhalla",
        year = 2029,
        summary = "Forecast: Java value classes leave preview and become final, so a small object with no identity sits in an array without a heap object for each element.",
        details = "Forecast. The value classes preview in JEP 401 was reported to target JDK 28, in March 2027, and a news summary expects it to stay in preview past JDK 29. The forecast is that it becomes final in JDK 33, the long-term-support release expected in September 2029. The year is a guess, and the JEP text was still changing.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.LANGUAGE,
        section = "Python",
        title = "Python with the free-threaded build as the default",
        originalTitle = "PEP 703 and PEP 779 (free-threaded CPython)",
        author = "Python core developers",
        year = 2031,
        summary = "Forecast: the Python build without a global interpreter lock becomes the default, so threads run in parallel by default.",
        details = "Forecast. Python 3.14 supports the free-threaded build officially, under PEP 779, but users still choose it when they build or install Python. The forecast is that the free-threaded build becomes the default around 2031, once the popular packages support it. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.LANGUAGE,
        section = "Agents",
        title = "Tool calls checked by the compiler",
        originalTitle = "A category of languages, not one language",
        author = "Forecast written for this database",
        year = 2029,
        summary = "Forecast: a language treats a tool's schema as a type, so a call with a wrong argument fails to compile instead of failing at run time.",
        details = "Forecast. Tool calls are JSON checked at run time, so a wrong argument shows up only when the call is made. The forecast is that a general-purpose language gives tool schemas a type, and that the compiler rejects a mismatched call. The year is a guess; this may end up as a library rather than a language.",
        rare = true,
    ),

    entry(
        databaseId = 9,
        kind = Kind.LANGUAGE,
        section = "Quantum",
        title = "Quantum circuits in ordinary compilers",
        originalTitle = "Qiskit, Q# and CUDA-Q, the existing quantum SDKs",
        author = "Forecast written for this database",
        year = 2032,
        summary = "Forecast: quantum circuits become one more compiler target, and a normal program calls a quantum routine the way it calls a math library.",
        details = "Forecast. Qiskit, Q# and CUDA-Q let a program build circuits, but the code is written in a separate SDK and runs on a separate service. IBM's public roadmap aims at a fault-tolerant machine with 200 logical qubits in 2029, which is the step this forecast needs. The year is a guess.",
    ),
)
