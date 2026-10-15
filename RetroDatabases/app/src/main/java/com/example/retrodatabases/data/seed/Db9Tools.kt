package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 2026 to 2036, as a forecast: prompt checks, proof assistants, signed bills of materials, carbon meters and sandboxes. */
val db9Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.TOOL,
        section = "Prompts",
        title = "Prompt linters and tests in CI",
        originalTitle = "Evaluation suites for prompts",
        author = "Forecast written for this database",
        year = 2027,
        summary = "Forecast: prompt files get linting, schema checks and regression tests in continuous integration, the way code does today.",
        details = "Forecast. Teams already keep evaluation suites for prompts, and a few tools lint them. The forecast is that prompt changes get the same review and tests as code changes, and that a broken prompt fails the build. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.TOOL,
        section = "Verification",
        title = "A proof assistant for everyday functions",
        originalTitle = "Lean, Dafny and Kani, the existing verifiers",
        author = "Forecast written for this database",
        year = 2028,
        summary = "Forecast: an editor proves small functions correct as the developer writes them, with an assistant that drafts the proof.",
        details = "Forecast. Lean, Dafny and the Rust checker Kani already prove properties of real code, but writing the proofs takes expert time. The forecast is that assistants draft proofs for short functions, and that the editor shows a check mark when a function meets its spec. The year is a guess.",
        rare = true,
    ),

    entry(
        databaseId = 9,
        kind = Kind.TOOL,
        section = "Supply chain",
        title = "Signed software bills of materials checked on install",
        originalTitle = "CycloneDX and SPDX, existing bill-of-materials formats",
        author = "Forecast written for this database",
        year = 2027,
        summary = "Forecast: installing a package also checks its signed bill of materials, and a missing one blocks the install in regulated projects.",
        details = "Forecast. The EU Cyber Resilience Act has required reporting of exploited vulnerabilities and severe incidents since 11 September 2026, and the rest of the act applies from 11 December 2027. The forecast is that bills of materials are checked on install by default, not only in audits. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.TOOL,
        section = "Green software",
        title = "A carbon meter in the CI pipeline",
        originalTitle = "Software Carbon Intensity specification, ISO/IEC 21031:2024",
        author = "Green Software Foundation",
        year = 2029,
        summary = "Forecast: each build reports an estimated carbon figure, and a pull request that goes over its budget fails the check.",
        details = "Forecast. The Software Carbon Intensity specification reached version 1.0 in late 2022 and became ISO/IEC 21031:2024 in March 2024, and the Foundation now names version 1.1 as its latest. The forecast is that CI systems report a figure like it by default, and that teams set carbon budgets the way they set test time limits. The year is a guess, and the energy figures are estimates.",
        rare = false,
    ),

    entry(
        databaseId = 9,
        kind = Kind.TOOL,
        section = "Supply chain",
        title = "Post-quantum signatures in package managers",
        originalTitle = "FIPS 204 (ML-DSA) and FIPS 205 (SLH-DSA)",
        author = "NIST",
        year = 2030,
        summary = "Forecast: package managers verify ML-DSA signatures next to the classic ones, so a package signed today still checks after quantum computers exist.",
        details = "Forecast. NIST published ML-DSA and SLH-DSA as FIPS 204 and FIPS 205 in August 2024, and OpenSSL 3.5 already includes them. The forecast is that package managers add them as a second signature, and that classic signatures are retired after the transition deadlines. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.TOOL,
        section = "Agents",
        title = "A sandbox for every tool call an agent makes",
        originalTitle = "Firecracker microVMs and gVisor, both in production",
        author = "Forecast written for this database",
        year = 2027,
        summary = "Forecast: each tool an agent calls runs in its own microVM, with no network unless the call asks for it.",
        details = "Forecast. Firecracker runs microVMs for serverless functions, and gVisor isolates containers, and both are in production today. The forecast is that agent platforms run each tool call inside one of these, with the network closed by default. The year is a guess.",
    ),
)
