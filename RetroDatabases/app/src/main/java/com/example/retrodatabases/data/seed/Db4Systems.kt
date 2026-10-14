package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 2010 to 2020: operating systems, mobile platforms, system services, runtimes and cloud services that changed how code was written and run. */
val db4Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Desktop systems",
        title = "macOS Big Sur",
        originalTitle = "macOS 11 Big Sur",
        author = "Apple",
        year = 2020,
        summary = "The macOS release that ran natively on Apple silicon and ran Intel apps through the Rosetta 2 translation layer.",
        details = "macOS 11 Big Sur shipped in November 2020, in the same month as the first Macs with Apple's own M1 chip. " +
            "Rosetta 2 translates existing Intel apps as they run, so developers could also ship universal binaries that contain both kinds of machine code.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Desktop systems",
        title = "Windows 8",
        originalTitle = "Windows 8",
        author = "Microsoft",
        year = 2012,
        summary = "The version of Windows built around touch, with a tiled Start screen and full-screen apps on the WinRT API.",
        details = "Microsoft released Windows 8 in October 2012. The Start screen was made of live tiles designed for touch, and store apps ran full screen. " +
            "Many desktop users disliked the change, and Windows 8.1 in 2013 brought back a Start button.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Mobile systems",
        title = "Windows Phone 8",
        originalTitle = "Windows Phone 8",
        author = "Microsoft and Nokia",
        year = 2012,
        summary = "A phone system that shared its kernel with Windows 8 and ran apps written in C# and XAML.",
        details = "Windows Phone 8 arrived in October 2012 on the Windows NT kernel, so phones and PCs could share code. " +
            "Developers used the same C#, XAML and tools as desktop Windows apps. The platform never won a large share of the market.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Mobile systems",
        title = "Tizen",
        originalTitle = "Tizen 1.0 (Larkspur)",
        author = "Samsung and the Linux Foundation",
        year = 2012,
        summary = "A Linux-based system from Samsung and the Linux Foundation, whose apps could be written in HTML5 and JavaScript.",
        details = "Tizen 1.0, codenamed Larkspur, came out in 2012. Sources give April or May 2012 for it. " +
            "Samsung later used Tizen for smart TVs and watches, and the system stayed a niche platform for phones.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Mobile systems",
        title = "Firefox OS",
        originalTitle = "Firefox OS",
        author = "Mozilla",
        year = 2013,
        summary = "Mozilla's phone system in which every app is a web app written in HTML, CSS and JavaScript.",
        details = "The first commercial phones, the Geeksphone Keon and Peak, were announced in January 2013 and went on sale in April 2013, with Telefónica helping to sell them. " +
            "Apps were web apps installed from a store, so any web developer could write one. Mozilla stopped developing the system in 2016.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Desktop systems",
        title = "Chrome OS",
        originalTitle = "Chrome OS (Chromebooks)",
        author = "Google",
        year = 2011,
        summary = "A Linux-based system in which the browser is the main program and most files live in the cloud.",
        details = "Google published the Chromium OS source in 2009. The first Chromebooks, from Samsung and Acer, went on sale in June 2011 and started straight into a browser. " +
            "Web apps were the only programs, and schools and businesses adopted the machines for managed accounts.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Mobile systems",
        title = "Android 5.0 Lollipop and ART",
        originalTitle = "Android 5.0 Lollipop (ART runtime)",
        author = "Google and the Android team",
        year = 2014,
        summary = "The Android release that made ART, the runtime that compiles app code to machine code, the default.",
        details = "Android 5.0 Lollipop came out in November 2014. ART had been offered as an option in KitKat in 2013, and Lollipop replaced the older Dalvik runtime with it. " +
            "ART compiled app code to machine code when an app was installed, so the phone spent less time interpreting code while the app ran.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Linux",
        title = "systemd",
        originalTitle = "systemd",
        author = "Lennart Poettering, Kay Sievers and Red Hat",
        year = 2010,
        summary = "A Linux init system that starts services in parallel, watches them and keeps their logs in a journal.",
        details = "Development began at Red Hat in 2010. Release dates differ by source: an infobox gives 30 March 2010, an industry article gives 18 May 2010 for version 1, " +
            "and a packaging changelog gives 7 July 2010 for the first upstream release. Many distributions made systemd their default init system in the years that followed.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Linux",
        title = "Wayland",
        originalTitle = "Wayland 1.0 with Weston",
        author = "Kristian Høgsberg and the Wayland community",
        year = 2012,
        summary = "A display protocol in which the compositor draws every window, which replaces the older X11 model.",
        details = "Wayland 1.0 and the Weston reference compositor came out on 22 October 2012. From then on the protocol and client API were fixed, so later versions only added to them. " +
            "Adoption was slow, and desktops moved to Wayland over the following years.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Experimental systems",
        title = "Redox OS",
        originalTitle = "Redox OS",
        author = "Jeremy Soller and the Redox community",
        year = 2015,
        summary = "A Unix-like operating system written in Rust, with a microkernel and most drivers running as separate programs.",
        details = "Redox first appeared publicly on GitHub in April 2015 under the MIT license. Its kernel is a microkernel, so drivers and file systems run as ordinary programs in user space. " +
            "The project is an attempt to build a whole operating system in a memory-safe language.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Experimental systems",
        title = "Fuchsia",
        originalTitle = "Fuchsia",
        author = "Google",
        year = 2016,
        summary = "Google's experimental operating system, built on its own microkernel and not on the Linux kernel.",
        details = "Fuchsia's code appeared in a public repository in August 2016, with no announcement. The kernel was first called Magenta and was later named Zircon. " +
            "The first product to run Fuchsia was a smart display, the Nest Hub, in 2021.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Web platforms",
        title = "WebAssembly",
        originalTitle = "WebAssembly (MVP)",
        author = "Google, Mozilla, Microsoft, Apple and the W3C community",
        year = 2017,
        summary = "A compact binary format that browsers run next to JavaScript, so compiled C, C++ and Rust code can run in a page.",
        details = "The project started in 2015, when a demo ran a game in several browsers. Browsers began shipping the first version in March 2017, and every major engine supported it by the end of that year. " +
            "Compilers such as Emscripten and LLVM can produce WebAssembly from other languages.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Cloud and containers",
        title = "Kubernetes",
        originalTitle = "Kubernetes 1.0",
        author = "Google engineers, then the Cloud Native Computing Foundation",
        year = 2014,
        summary = "A system that schedules containers across a cluster of machines and restarts the ones that fail.",
        details = "Google open-sourced Kubernetes in June 2014. It drew on Borg, the cluster manager Google had run internally for years. " +
            "Version 1.0 came out in July 2015, when the project was handed to the new Cloud Native Computing Foundation.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Serverless",
        title = "AWS Lambda",
        originalTitle = "AWS Lambda",
        author = "Amazon Web Services",
        year = 2014,
        summary = "A service that runs a function on demand, so the developer manages no servers.",
        details = "Amazon announced Lambda in November 2014 at its re:Invent conference. A developer uploads a function, and the service runs it for each event, " +
            "such as a file upload or an HTTP request. The customer pays only for the time the function runs.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Serverless",
        title = "Firecracker",
        originalTitle = "Firecracker microVM",
        author = "Amazon Web Services",
        year = 2018,
        summary = "A minimal virtual machine monitor, built on KVM, that starts small virtual machines for functions and containers.",
        details = "Amazon unveiled Firecracker at re:Invent in late November 2018 and released it as open source under the Apache 2.0 license. " +
            "It is written in Rust, and Lambda and Fargate use it to isolate their workloads.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.SYSTEM,
        section = "Runtimes",
        title = "Java 9 modules",
        originalTitle = "Java Platform Module System (Project Jigsaw)",
        author = "Oracle and the OpenJDK community",
        year = 2017,
        summary = "A module system built into Java, in which a program declares which modules it needs and which it exports.",
        details = "Java 9 shipped in September 2017 with the module system that the Jigsaw project had worked on for years. " +
            "A program can be packaged with only the parts of the Java platform it uses, which made small runtime images possible.",
    ),
)
