package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 2020 to 2026: operating systems, platforms, system services, standards and cloud tools that changed how code was written and run. */
val db5Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Desktop systems",
        title = "Windows 11",
        originalTitle = "Windows 11",
        author = "Microsoft",
        year = 2021,
        summary = "The Windows release with a centred taskbar and a new Start menu, and the first to require a TPM 2.0 security chip.",
        details = "Microsoft released Windows 11 on 5 October 2021. It raised the hardware bar: a TPM 2.0 chip and a supported processor became requirements, " +
            "which left many older PCs without a free upgrade.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Mobile systems",
        title = "Android 12 and Material You",
        originalTitle = "Android 12",
        author = "Google and the Android team",
        year = 2021,
        summary = "Dynamic colour theming that builds an app palette from the wallpaper, and new privacy indicators.",
        details = "Android 12 came out in October 2021 with Material You, a colour system that takes its palette from the user's wallpaper. " +
            "It also showed an indicator when an app used the camera or the microphone.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Linux",
        title = "Linux 6.1 and Rust",
        originalTitle = "Linux kernel 6.1",
        author = "Linus Torvalds and the kernel community",
        year = 2022,
        summary = "The first mainline kernel with Rust support, which let new drivers be written in a memory-safe language, marked as experimental.",
        details = "Linux 6.1 shipped in December 2022 with Rust support merged into the mainline tree. The support was labelled experimental, " +
            "and it let kernel developers write new drivers in Rust alongside the C code.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Cloud and containers",
        title = "Kubernetes 1.24 and dockershim",
        originalTitle = "Kubernetes 1.24",
        author = "The Kubernetes release team",
        year = 2022,
        summary = "The release that removed dockershim, so clusters talk to containerd or CRI-O directly instead of through Docker Engine.",
        details = "Kubernetes 1.24 came out in May 2022 and removed dockershim, the built-in bridge to Docker Engine. The removal had been announced in version 1.20 in December 2020. " +
            "Clusters moved to a runtime that speaks the Container Runtime Interface, such as containerd or CRI-O.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Smart home",
        title = "Matter 1.0",
        originalTitle = "Matter 1.0 specification",
        author = "Connectivity Standards Alliance, formerly the Zigbee Alliance",
        year = 2022,
        summary = "A smart-home standard that lets devices from different makers work together over Thread, Wi-Fi and Ethernet.",
        details = "The Connectivity Standards Alliance published Matter 1.0 in early October 2022 after repeated delays. Devices use IP networking, and Bluetooth Low Energy handles setup. " +
            "Certification opened at the same time, with eight test labs ready to check products.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Web platforms",
        title = "WebGPU",
        originalTitle = "WebGPU in Chrome 113",
        author = "The W3C GPU for the Web community group and the browser teams",
        year = 2023,
        summary = "A web API for modern GPU programming, which Chrome switched on by default in 2023.",
        details = "Chrome 113 switched WebGPU on by default in 2023, first on ChromeOS, macOS and Windows. Firefox and Safari were still building their own versions at the time. " +
            "WebGPU succeeds WebGL for rendering, and it adds compute shaders for general-purpose GPU work.",
        example = """
            const adapter = await navigator.gpu.requestAdapter();
            const device = await adapter.requestDevice();
            console.log(device.limits.maxBufferSize);
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Spatial computing",
        title = "visionOS",
        originalTitle = "visionOS 1",
        author = "Apple",
        year = 2024,
        summary = "Apple's operating system for the Vision Pro headset, where apps are windows and volumes built with SwiftUI and RealityKit.",
        details = "visionOS came with the Vision Pro in February 2024. Apps use SwiftUI for their windows and RealityKit for 3D content, " +
            "and compatible iPad apps can run in a window.",
        example = """
            import SwiftUI

            struct ContentView: View {
                var body: some View {
                    Text("Hello, visionOS")
                        .font(.largeTitle)
                }
            }
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Mobile systems",
        title = "HarmonyOS NEXT",
        originalTitle = "HarmonyOS NEXT",
        author = "Huawei",
        year = 2024,
        summary = "Huawei's operating system without the Android compatibility layer, where apps are written in ArkTS.",
        details = "Huawei opened a developer preview of HarmonyOS NEXT in early 2024, and a developer beta followed in mid-2024. Without the Android layer, existing Android apps do not run, " +
            "so developers rewrite them in ArkTS.",
        example = """
            @Entry
            @Component
            struct Index {
              build() {
                Column() {
                  Text('Hello HarmonyOS')
                }
              }
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "WebAssembly",
        title = "WASI 0.2",
        originalTitle = "WASI Preview 2 (WASI 0.2)",
        author = "The WASI subgroup of the Bytecode Alliance",
        year = 2024,
        summary = "The stable WASI release that describes system interfaces as WebAssembly components, written in the WIT language.",
        details = "The WASI subgroup approved WASI 0.2, also called Preview 2, in January 2024. It is built on the component model, so languages can call each other's components through WIT interfaces. " +
            "Two worlds ship with it: a command-line profile and an HTTP profile.",
        example = """
            package example:greeter;

            world greeter {
              export greet: func(name: string) -> string;
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Databases",
        title = "Valkey",
        originalTitle = "Valkey (fork of Redis 7.2.4)",
        author = "The Linux Foundation and the Valkey contributors",
        year = 2024,
        summary = "An open-source fork of Redis under the BSD licence, started after Redis changed its licence in 2024.",
        details = "Valkey appeared in late March 2024, forked from Redis 7.2.4, after Redis moved to dual source-available licences. The Linux Foundation governs the project, " +
            "and several large cloud providers backed it from the start.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "AI tooling",
        title = "Model Context Protocol",
        originalTitle = "MCP",
        author = "Anthropic, then the Agentic AI Foundation",
        year = 2024,
        summary = "An open protocol that connects AI assistants to tools and data sources through servers with one common interface.",
        details = "Anthropic published the protocol on 25 November 2024. A server exposes files, databases or APIs, and any client that speaks the protocol can use them. " +
            "In December 2025 the project moved to the Agentic AI Foundation under the Linux Foundation.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Desktop systems",
        title = "Windows 10 reaches end of support",
        originalTitle = "Windows 10 support end",
        author = "Microsoft",
        year = 2025,
        summary = "The date on which Microsoft ended routine security updates for Windows 10, on 14 October 2025.",
        details = "Windows 10 stopped receiving routine security updates on 14 October 2025. Consumers could join an extended security updates programme for one more year, " +
            "and Microsoft advised moving to Windows 11 where the hardware allowed it.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Desktop systems",
        title = "macOS Tahoe 26",
        originalTitle = "macOS 26 Tahoe",
        author = "Apple",
        year = 2025,
        summary = "The macOS release that Apple numbered 26, skipping 16, so every platform now shares a year-based version number.",
        details = "Apple unveiled macOS Tahoe at WWDC in June 2025. Build tools and scripts had to cope with the new number, " +
            "and some compiler drivers were changed for the new mapping of Darwin versions.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Desktop systems",
        title = "macOS 27 Golden Gate",
        originalTitle = "macOS 27",
        author = "Apple",
        year = 2026,
        summary = "The macOS release announced in June 2026, which runs only on Macs with Apple silicon.",
        details = "Apple announced macOS 27 at WWDC on 8 June 2026, under the name Golden Gate. Compatible Macs could install it from 14 September 2026. " +
            "Intel Macs are no longer supported, so the oldest eligible machines use M1 chips.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.SYSTEM,
        section = "Linux",
        title = "Linux 7.0",
        originalTitle = "Linux kernel 7.0",
        author = "Linus Torvalds and the kernel community",
        year = 2026,
        summary = "The kernel release that moved the major version from 6 to 7, for numbering reasons rather than a change of design.",
        details = "Linus Torvalds released Linux 7.0 on 12 April 2026 after seven release candidates. He had named it in the announcement of 6.19, and he said the major number would tick over " +
            "about every three and a half years.",
    ),
)
