package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 2020 to 2026: headsets, boards, handhelds, laptops and gadgets that programmers wrote code for. */
val db5Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Virtual reality",
        title = "Oculus Quest 2",
        originalTitle = "Oculus Quest 2",
        author = "Oculus, then Facebook",
        year = 2020,
        summary = "A standalone VR headset that runs its own Android-based system, so apps do not need a PC.",
        details = "Quest 2 went on sale in October 2020. It runs an Android-based system on its own chip, and developers build apps with the Android-based SDK, " +
            "with Unity and Unreal Engine as common choices.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Microcontroller boards",
        title = "Raspberry Pi Pico",
        originalTitle = "Raspberry Pi Pico",
        author = "Raspberry Pi Foundation",
        year = 2021,
        summary = "A four-dollar microcontroller board built around the Foundation's RP2040 chip, programmed in MicroPython, C or CircuitPython.",
        details = "The Pico launched in January 2021 at four dollars. The RP2040 is a dual-core Arm chip designed by the Foundation, " +
            "and its C SDK and MicroPython port made the board a common first choice for microcontroller classes.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Laptops",
        title = "Framework Laptop 13",
        originalTitle = "Framework Laptop 13 (11th Gen Intel)",
        author = "Framework Computer",
        year = 2021,
        summary = "A laptop built to be repaired and upgraded by its owner, with replaceable ports, memory and storage modules.",
        details = "The first Framework Laptop 13 orders shipped in July 2021 with an 11th-generation Intel processor. Owners could swap memory, storage and port modules, " +
            "and iFixit gave the laptop a perfect repairability score.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Handhelds",
        title = "Analogue Pocket",
        originalTitle = "Analogue Pocket",
        author = "Analogue",
        year = 2021,
        summary = "A handheld that runs Game Boy and Game Boy Advance cartridges on FPGA chips that reproduce the original hardware.",
        details = "Analogue began shipping the Pocket in mid-December 2021 after several delays. Its two FPGA chips are programmed to behave like the original Game Boy processors, " +
            "so games need no software emulation.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Single-board computers",
        title = "Raspberry Pi Zero 2 W",
        originalTitle = "Raspberry Pi Zero 2 W",
        author = "Raspberry Pi Foundation",
        year = 2021,
        summary = "A Zero-sized Raspberry Pi with a quad-core Arm processor and wireless networking, sold for fifteen dollars.",
        details = "The Zero 2 W arrived in October 2021 at fifteen dollars. It has four Cortex-A53 cores at 1 GHz and 512 MB of memory in the footprint of the original Zero W, " +
            "and the Foundation claimed about five times its multi-threaded speed.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Steam Deck",
        originalTitle = "Steam Deck (LCD)",
        author = "Valve",
        year = 2022,
        summary = "A handheld PC running SteamOS, a Linux-based system, which plays most Windows games through the Proton compatibility layer.",
        details = "Valve released the Steam Deck in February 2022. Its SteamOS is based on Arch Linux, and the Proton layer runs most Windows games on it, " +
            "so developers can ship Linux builds or rely on Proton.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Handhelds",
        title = "Playdate",
        originalTitle = "Playdate handheld",
        author = "Panic",
        year = 2022,
        summary = "A small monochrome handheld with a crank, whose games are written in Lua or C with the free Playdate SDK.",
        details = "Panic delayed the first Playdate shipments into 2022 after a battery defect. Games are written in Lua or C with the Playdate SDK, and the crank is an input that games can use. " +
            "The company had shipped tens of thousands of units by early 2024.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Single-board computers",
        title = "Raspberry Pi 5",
        originalTitle = "Raspberry Pi 5",
        author = "Raspberry Pi Foundation",
        year = 2023,
        summary = "A Raspberry Pi with a quad-core Arm processor at 2.4 GHz, a separate chip for input and output, and a PCI Express connector for fast storage.",
        details = "The Pi 5 arrived in October 2023. Its Cortex-A76 cores run at 2.4 GHz, and a separate RP1 chip handles USB, Ethernet and most input and output. " +
            "A PCI Express connector lets it use fast storage.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Virtual reality",
        title = "Meta Quest 3",
        originalTitle = "Meta Quest 3",
        author = "Meta",
        year = 2023,
        summary = "A mixed-reality headset with colour passthrough cameras, which runs the same Android-based system as Quest 2.",
        details = "Quest 3 shipped in October 2023 with colour passthrough cameras and a faster processor. It keeps the Android-based system, " +
            "so most apps written for Quest 2 run on it with the same SDKs.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Spatial computing",
        title = "Apple Vision Pro",
        originalTitle = "Apple Vision Pro (first generation)",
        author = "Apple",
        year = 2024,
        summary = "A headset with eye and hand input, which runs visionOS and apps written in SwiftUI and RealityKit.",
        details = "Apple released Vision Pro in February 2024. Input comes from the eyes and hands, so there are no controllers. " +
            "Developers build windowed apps in SwiftUI and 3D content in RealityKit.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Microcontroller boards",
        title = "Raspberry Pi Pico 2",
        originalTitle = "Raspberry Pi Pico 2 (RP2350)",
        author = "Raspberry Pi Foundation",
        year = 2024,
        summary = "A five-dollar board with the RP2350 chip, whose two cores can run Arm Cortex-M33 or RISC-V code.",
        details = "The Pico 2 was unveiled on 8 August 2024 at five dollars. The RP2350 chip has two cores that the firmware can set to Arm Cortex-M33 or to RISC-V, " +
            "and the board kept the Pico's size and pin layout.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Single-board computers",
        title = "Raspberry Pi 500",
        originalTitle = "Raspberry Pi 500 (keyboard computer)",
        author = "Raspberry Pi Foundation",
        year = 2024,
        summary = "A complete computer built into a keyboard, with a Raspberry Pi 5 board inside and a 15.6-inch monitor sold with it.",
        details = "The Pi 500 launched at the end of 2024 with a Raspberry Pi 5 board, 8 GB of memory and a 32 GB card with the operating system already installed. " +
            "It was aimed at schools and hobbyists who wanted a computer without a separate case.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.DEVICE,
        section = "Laptops",
        title = "Copilot+ PC",
        originalTitle = "Copilot+ PC (Snapdragon X Elite)",
        author = "Microsoft and Qualcomm",
        year = 2024,
        summary = "The class of Windows laptops with a neural processing unit for on-device AI, first sold with Arm-based Snapdragon X chips.",
        details = "Microsoft announced Copilot+ PCs in May 2024, and the first machines reached stores in June 2024. The launch models all used Qualcomm's Snapdragon X chips with Arm processors, " +
            "and Qualcomm rated the NPU at 45 trillion operations per second.",
    ),
)
