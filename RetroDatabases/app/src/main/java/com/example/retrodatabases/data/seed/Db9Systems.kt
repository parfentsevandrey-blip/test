package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 2026 to 2036, as a forecast: security, runtimes, the kernel, quantum services and agent permissions. */
val db9Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.SYSTEM,
        section = "Security",
        title = "Post-quantum key exchange by default in TLS",
        originalTitle = "OpenSSL 3.5, with ML-KEM, ML-DSA and SLH-DSA",
        author = "NIST and the OpenSSL project",
        year = 2028,
        summary = "Forecast: most TLS connections use a hybrid key exchange with a post-quantum algorithm, and no setting is needed to turn it on.",
        details = "Forecast. OpenSSL 3.5, released in April 2025, ships ML-KEM, ML-DSA and SLH-DSA, and its default TLS client offers the hybrid group X25519MLKEM768 next to plain X25519. The forecast is that servers and client libraries drop the classic-only offer by 2028, while post-quantum signatures take longer. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.SYSTEM,
        section = "Runtimes",
        title = "WebAssembly components as the plug-in format",
        originalTitle = "WASI 0.3, with native async in the component model",
        author = "Bytecode Alliance and the WebAssembly community",
        year = 2028,
        summary = "Forecast: applications load plug-ins as WebAssembly components with a stated list of permissions, and a plug-in runs without a process of its own.",
        details = "Forecast. WASI 0.3, with native async, was released in June 2026 according to wasi.dev, and runtimes have started to ship it. The forecast is that editors, databases and proxies use components for plug-ins by 2028, because a sandboxed component is cheaper than a process per plug-in. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.SYSTEM,
        section = "Kernel",
        title = "Rust drivers in the mainline Linux kernel",
        originalTitle = "Rust for Linux, no longer experimental since December 2025",
        author = "Rust for Linux project",
        year = 2029,
        summary = "Forecast: most new Linux drivers are written in Rust, and C drivers become the exception.",
        details = "Forecast. In December 2025 the kernel maintainers agreed that Rust is no longer experimental, and the Rust for Linux project declared the experiment finished. The forecast is that most new drivers are in Rust by 2029, although support across build options and hardware is still uneven. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.SYSTEM,
        section = "Quantum",
        title = "Quantum cloud runtimes",
        originalTitle = "Quantum services from IBM, Microsoft and Amazon",
        author = "Forecast written for this database",
        year = 2030,
        summary = "Forecast: quantum jobs run on a shared runtime with one job format, so the same program runs on the machines of several vendors.",
        details = "Forecast. Three large providers already sell access to quantum hardware through their own services, each with its own job format and billing. The forecast is that a common runtime and job format appear by 2030, so a program can move between vendors. The year is a guess.",
        rare = true,
    ),

    entry(
        databaseId = 9,
        kind = Kind.SYSTEM,
        section = "Boot",
        title = "Post-quantum firmware signing",
        originalTitle = "Hash-based signature schemes for firmware, NIST SP 800-208 (2020)",
        author = "NIST and the firmware vendors",
        year = 2035,
        summary = "Forecast: phones and laptops check their boot firmware with hash-based post-quantum signatures, so forged firmware cannot be signed with a key that a quantum computer breaks.",
        details = "Forecast. NIST approved hash-based signature schemes for firmware in 2020, in special publication 800-208. The forecast is that most phones and laptops check boot firmware with such signatures by 2035, the year when classic signatures are expected to be disallowed under the draft transition plan. The year is a guess.",
    ),
)
