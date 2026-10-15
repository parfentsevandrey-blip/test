package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of 2020 to 2026: new standards and major releases, from C++20 and Java 17 to Go 1.26 and C# 14. */
val db5Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "C++20",
        originalTitle = "C++20 (ISO/IEC 14882:2020)",
        author = "Bjarne Stroustrup and the ISO C++ committee",
        year = 2020,
        summary = "The C++ revision with concepts, ranges, coroutines and modules, approved as an ISO standard at the end of 2020.",
        details = "The committee finished C++20 in 2020. Concepts let a template state the requirements on its arguments, and ranges let algorithms work on whole pipelines of views. " +
            "Coroutines and modules were standardised too, but compilers took years to support modules fully.",
        instructions = """
            1. Save the example as ranges.cpp.
            2. Compile it: g++ -std=c++20 -o ranges ranges.cpp
            3. Run it: ./ranges
        """,
        example = """
            #include <concepts>
            #include <iostream>
            #include <ranges>
            #include <vector>

            template <std::integral T>
            T twice(T value) {
                return value * 2;
            }

            int main() {
                std::vector<int> numbers{1, 2, 3, 4, 5, 6};
                auto doubledEvens = numbers
                    | std::views::filter([](int n) { return n % 2 == 0; })
                    | std::views::transform([](int n) { return twice(n); });
                for (int value : doubledEvens) {
                    std::cout << value << "\n";
                }
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Scripting",
        title = "PHP 8.0",
        originalTitle = "PHP 8.0",
        author = "The PHP development team",
        year = 2020,
        summary = "The PHP release with a JIT compiler, union types, attributes, named arguments and the match expression.",
        details = "PHP 8.0 was released in November 2020. Its match expression compares strictly and throws an error when no arm fits, and named arguments make long calls easier to read. " +
            "The JIT compiler and attributes changed how the engine runs and how libraries annotate their code.",
        instructions = """
            1. Save the example as labels.php.
            2. Run it: php labels.php
        """,
        example = """
            <?php
            function label(int <DOLLAR>code): string {
                return match (<DOLLAR>code) {
                    200 => "ok",
                    404 => "not found",
                    500, 503 => "server error",
                };
            }

            echo label(200), "\n";
            echo label(503), "\n";
            echo str_pad(string: "x", length: 3, pad_string: "-"), "\n";
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Scripting",
        title = "Ruby 3.0",
        originalTitle = "Ruby 3.0",
        author = "Yukihiro Matsumoto and the Ruby core team",
        year = 2020,
        summary = "The Ruby release with endless method definitions, separated keyword arguments, experimental Ractors and RBS type signatures.",
        details = "Ruby 3.0 was released on 25 December 2020. Endless method definitions let a one-line method be written with an equals sign, and keyword arguments were separated from positional hashes. " +
            "Ractors were added as an experimental way to run code in parallel, and RBS gave Ruby a standard format for type signatures.",
        instructions = """
            1. Save the example as square.rb.
            2. Run it: ruby square.rb
        """,
        example = """
            def square(x) = x * x

            def greet(name:, greeting: "Hello")
              "#{greeting}, #{name}"
            end

            puts square(4)
            puts greet(name: "Ann")
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Swift async and await",
        originalTitle = "Swift 5.5 concurrency",
        author = "Apple and the Swift team",
        year = 2021,
        summary = "Built-in async and await, actors and structured tasks, which replaced most callback-based code in Swift.",
        details = "Apple released Swift 5.5 in September 2021. Functions could be marked async and called with await, and actors protected their state from data races. " +
            "Structured tasks let a parent wait for its children, which made cancellation easier to follow.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Python 3.10 pattern matching",
        originalTitle = "PEP 634 structural pattern matching",
        author = "Brandt Bucher, Guido van Rossum and the Python core developers",
        year = 2021,
        summary = "The match statement, which picks a branch by the shape of a value and binds parts of it to names.",
        details = "Python 3.10 came out in October 2021 with structural pattern matching, specified in PEP 634. A case can match literals, sequences, mappings and class instances, " +
            "and a guard can add a condition. Long chains of if statements became shorter and easier to check.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Java 17 LTS",
        originalTitle = "Java SE 17",
        author = "Oracle and the OpenJDK community",
        year = 2021,
        summary = "The long-term support release where records and sealed classes became final, so data types and closed hierarchies are short to write.",
        details = "Java 17 shipped in September 2021 as a long-term support release. Records give immutable data classes with generated accessors, equality and printing, " +
            "and sealed classes list the only types that may extend a class. Many teams moved their services to 17 in the following years.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Go generics",
        originalTitle = "Go 1.18 type parameters",
        author = "The Go team",
        year = 2022,
        summary = "Type parameters for functions and types, which ended years of copied code for each element type.",
        details = "Go 1.18 came out in March 2022 with type parameters. A function can accept any type that meets a constraint, and a generic type such as a list can hold any element type. " +
            "The design had been argued over for years, and libraries adopted it gradually.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Dart 3",
        originalTitle = "Dart 3.0",
        author = "Google and the Dart team",
        year = 2023,
        summary = "The Dart release with records, pattern matching, class modifiers and sound null safety.",
        details = "Google announced Dart 3 at I/O on 10 May 2023. Records let a function return several values without a class, and patterns take them apart in a switch or an assignment. " +
            "Null safety became sound, so a non-nullable type can never hold null at run time.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Java 21 LTS",
        originalTitle = "Java SE 21",
        author = "Oracle and the OpenJDK community",
        year = 2023,
        summary = "The long-term support release with virtual threads, record patterns and pattern matching for switch, all of them final.",
        details = "Java 21 shipped in September 2023. Virtual threads, from Project Loom, made it cheap to run very many blocking tasks, and record patterns and switch patterns take data apart without casts. " +
            "Each of these had spent time as a preview before it became final.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Systems and scripting",
        title = "Nim 2.0",
        originalTitle = "Nim 2.0",
        author = "Andreas Rumpf and the Nim community",
        year = 2023,
        summary = "A statically typed language that compiles to C, C++ or JavaScript, with the ORC memory manager as its default.",
        details = "Nim 2.0 was released in early August 2023. Its headline change was ORC, a reference-counting memory manager with a cycle collector, which became the default in place of ARC. " +
            "The release also sharpened type inference, mapped 64-bit integers to BigInt in JavaScript output and improved C++ interop.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Systems and AI",
        title = "Mojo",
        originalTitle = "Mojo (Modular)",
        author = "Modular, led by Chris Lattner",
        year = 2023,
        summary = "A language with Python-like syntax and native compilation, aimed at fast AI and systems code.",
        details = "Modular made Mojo publicly available in early May 2023. Its syntax is close to Python, and it compiles to fast native code, so Python programmers can reach C-like speed in the parts that need it. " +
            "A Linux build followed later that year.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Documents",
        title = "Typst",
        originalTitle = "Typst markup language",
        author = "Laurenz Mädje, Martin Haug and Typst GmbH",
        year = 2023,
        summary = "A markup language for documents with a fast compiler, built as a simpler alternative to LaTeX.",
        details = "Typst grew out of a university project and became open source in March 2023 under the Apache 2.0 licence. " +
            "Its markup is simpler than LaTeX, and its compiler is fast enough for a live preview while the author types.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Functional and typed",
        title = "Gleam 1.0",
        originalTitle = "Gleam 1.0",
        author = "Louis Pilfold and the Gleam community",
        year = 2024,
        summary = "A type-safe functional language for the Erlang virtual machine, which can call Erlang and Elixir libraries.",
        details = "Gleam 1.0 was released on 4 March 2024 with a promise of backward compatibility under semantic versioning. " +
            "It runs on the BEAM, the Erlang virtual machine, and can call Erlang and Elixir libraries, so Gleam code can join an existing system without a rewrite.",
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Kotlin 2.0",
        originalTitle = "Kotlin 2.0.0 with the K2 compiler",
        author = "JetBrains",
        year = 2024,
        summary = "The Kotlin release where the K2 compiler became stable, with a rebuilt front end and faster compilation.",
        details = "JetBrains released Kotlin 2.0 on 21 May 2024 with the K2 compiler stable for everyone. The front end was rebuilt from scratch, and JetBrains reported build speed-ups of up to twice on some projects. " +
            "Compose Multiplatform gained first-party backing, and the Compose compiler came with Kotlin itself.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Swift 6",
        originalTitle = "Swift 6 language mode",
        author = "Apple and the Swift team",
        year = 2024,
        summary = "The Swift release whose language mode checks for data races at compile time, which turns many old warnings into errors.",
        details = "Swift 6 arrived in September 2024. Its strict concurrency checking finds data races while the code is compiled, and switching the mode on turns many earlier warnings into errors. " +
            "Migration usually happens in steps, because flipping everything at once can produce hundreds of diagnostics.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Python 3.14",
        originalTitle = "Python 3.14",
        author = "The Python Software Foundation and the core developers",
        year = 2025,
        summary = "The release that made the free-threaded build officially supported, and that added template strings and deferred annotations.",
        details = "Python 3.14 became generally available on 7 October 2025. The free-threaded build, which runs threads in parallel without the global interpreter lock, is officially supported but still opt-in (PEP 779). " +
            "Template strings (PEP 750) let libraries process literal text and interpolated values safely, and annotations are evaluated lazily.",
        example = """
            name = "Ann"
            greeting = t"Hello {name}"
            print(type(greeting))
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Rust 2024 edition",
        originalTitle = "Rust 1.85 and the 2024 edition",
        author = "The Rust team",
        year = 2025,
        summary = "The edition shipped with Rust 1.85, with let chains, changed temporary scopes and a required unsafe on extern blocks.",
        details = "Rust 1.85.0 shipped on 20 February 2025 and made the 2024 edition stable. Breaking changes stay behind an edition switch, so older projects keep building. " +
            "The edition changed how temporaries live in if-let and tail expressions, and it requires extern blocks to be marked unsafe.",
        instructions = """
            1. Save the example as chains.rs.
            2. Compile it: rustc --edition 2024 chains.rs -o chains
            3. Run it: ./chains
        """,
        example = """
            fn main() {
                let scores = [3, 8, 12];
                if let Some(first) = scores.first() && *first > 2 {
                    println!("first score {first} is above two");
                }
            }
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Java 25 LTS",
        originalTitle = "Java SE 25",
        author = "Oracle and the OpenJDK community",
        year = 2025,
        summary = "The long-term support release with compact source files, flexible constructor bodies and scoped values, all final.",
        details = "Java 25 became generally available on 16 September 2025 and carries 18 JEPs. Compact source files let a beginner write a complete program without a class around it, " +
            "and flexible constructor bodies allow checks before the call to super or this. Scoped values replace many uses of ThreadLocal.",
        example = """
            void main() {
                System.out.println("Hello from a compact source file");
            }
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "C# 14 and .NET 10",
        originalTitle = "C# 14 with .NET 10 LTS",
        author = "Microsoft and the .NET team",
        year = 2025,
        summary = "The C# version that shipped with the .NET 10 long-term support release, with extension members and file-based programs.",
        details = "Microsoft released .NET 10 as a long-term support version on 11 November 2025, and projects that target it default to C# 14. " +
            "Extension members add properties and operators to existing types, and a single .cs file can run as a program without a project file.",
    ),

    entry(
        databaseId = 5,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Go 1.26",
        originalTitle = "Go 1.26",
        author = "The Go team",
        year = 2026,
        summary = "The Go release that lets new take an expression, and let generic types refer to themselves in their own type parameters.",
        details = "Go 1.26 was released on 10 February 2026. With the expression form of new, a pointer to a starting value takes one call, and a generic type may now refer to itself in its own type parameter list. " +
            "The Green Tea garbage collector became the default, and the go fix command gained a set of modernizers.",
    ),
)
