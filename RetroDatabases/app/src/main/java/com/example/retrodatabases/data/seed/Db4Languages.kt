package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of 2010 to 2020: the systems and safety languages, typed web languages, functional and data languages, and some playful ones. */
val db4Languages: List<RecordEntity> = listOf(

    // ---------- Systems and safety ----------

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Systems and safety",
        title = "Rust",
        originalTitle = "Rust 1.0",
        author = "Graydon Hoare, then the Rust team at Mozilla",
        year = 2015,
        summary = "A systems language with ownership and borrowing, which checks memory safety at compile time without a garbage collector.",
        details = "Hoare began Rust as a personal project in 2006, and Mozilla sponsored it from 2009. Rust 1.0 came out in May 2015. " +
            "The borrow checker tracks who owns each value and who may read or write it, so many memory bugs are rejected before the program runs.",
        example = """
            fn main() {
                let names = vec![String::from("Ann"), String::from("Bob")];
                let first = &names[0];
                println!("first: {}", first);
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Systems and safety",
        title = "Swift",
        originalTitle = "Swift 1.0",
        author = "Chris Lattner and the Apple team",
        year = 2014,
        summary = "Apple's modern language for iOS and macOS, unveiled in 2014 as a safer and shorter replacement for Objective-C.",
        details = "Apple unveiled Swift at its developer conference in June 2014. It made missing values explicit with optionals, and it added closures, generics and value types. " +
            "Swift was open-sourced in 2015, and developers moved their apps to it over the following years.",
        example = """
            let names = ["Ann", "Bob"]
            for name in names where name.hasPrefix("A") {
                print("Hello, \(name)")
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Systems and safety",
        title = "Zig",
        originalTitle = "Zig",
        author = "Andrew Kelley",
        year = 2016,
        summary = "A small systems language that avoids hidden control flow and hidden allocations, aiming to be a simpler replacement for C.",
        details = "Kelley's language made its public debut in February 2016. Compile-time execution, explicit memory allocators and the lack of hidden function calls " +
            "make the cost of each line visible. For years after its debut Zig stayed below version 1.0, and the design kept changing in public.",
        example = """
            const std = @import("std");

            pub fn main() void {
                std.debug.print("hello from Zig\n", .{});
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Systems and safety",
        title = "Pony",
        originalTitle = "Pony",
        author = "Sylvan Clebsch and the Pony team",
        year = 2015,
        summary = "An actor-based language whose reference capabilities rule out data races at compile time.",
        details = "Clebsch developed Pony in research at Imperial College London. Its type system tracks which references may be read, written or shared between actors, " +
            "so the compiler can prove that concurrent code has no data races. The language first appeared in 2015.",
        example = """
            actor Counter
              var count: U32 = 0
              be increment() => count = count + 1
              be report(out: OutStream) => out.print(count.string())
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Systems and safety",
        title = "Crystal",
        originalTitle = "Crystal",
        author = "Ary Borenszweig, Juan Wajnerman and the Crystal team",
        year = 2014,
        summary = "A language with Ruby-like syntax that infers static types and compiles to fast native code through LLVM.",
        details = "Crystal debuted publicly in June 2014. It keeps Ruby's readable syntax, but it infers types at compile time and generates native code, " +
            "so programs run close to C speed while still reading like Ruby. Version 1.0 arrived in 2021.",
        example = """
            def greet(name : String) : String
              "Hello, #{name}"
            end

            puts greet("Ann")
        """,
        rare = true,
    ),

    // ---------- Web and typed ----------

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Web and typed",
        title = "TypeScript",
        originalTitle = "TypeScript 0.8",
        author = "Anders Hejlsberg and the Microsoft team",
        year = 2012,
        summary = "JavaScript with optional static types that compile to plain JavaScript and catch many mistakes early.",
        details = "Microsoft released TypeScript as a public preview, version 0.8, in October 2012. Types are optional and erased at compile time, so the output is ordinary JavaScript that runs anywhere. " +
            "Version 1.0 followed at Microsoft's Build conference in 2014, and large codebases adopted the language for its editor support and safer refactoring.",
        example = """
            function total(prices: number[]): number {
              return prices.reduce((sum, p) => sum + p, 0);
            }
            console.log(total([2, 3, 5]));
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Web and typed",
        title = "Kotlin",
        originalTitle = "Kotlin 1.0",
        author = "JetBrains",
        year = 2016,
        summary = "A concise language for the JVM and Android, with null-safe types and full interoperability with Java.",
        details = "JetBrains began Kotlin in 2011, and version 1.0 came out in February 2016. Its null-safe types and short syntax made it popular with Android developers, " +
            "and Google announced it as an official Android language in 2017.",
        example = """
            data class Person(val name: String, val age: Int?)

            fun main() {
                val people = listOf(Person("Ann", 31), Person("Bob", null))
                people.forEach { println("<DOLLAR>{it.name} <DOLLAR>{it.age ?: "unknown"}") }
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Web and typed",
        title = "Dart",
        originalTitle = "Dart 1.0",
        author = "Lars Bak, Kasper Lund and the Google team",
        year = 2013,
        summary = "Google's language for web and mobile apps, compiled to JavaScript or to native code, and later the language of Flutter.",
        details = "Google announced Dart in 2011, and version 1.0 came out in November 2013. Its tools compile it to JavaScript for browsers and to native code for mobile. " +
            "The Flutter framework, first shown in 2017, made Dart the language of its user interfaces.",
        example = """
            void main() {
              final names = ['Ann', 'Bob'];
              for (final name in names) {
                print('Hello, <DOLLAR>name');
              }
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Web and typed",
        title = "Hack",
        originalTitle = "Hack (HHVM)",
        author = "Facebook engineers",
        year = 2014,
        summary = "A dialect of PHP with static types, built for the HipHop Virtual Machine at Facebook.",
        details = "Facebook introduced Hack in 2014 for its HipHop Virtual Machine (HHVM). Hack adds static types to PHP code gradually, " +
            "so large codebases can migrate without a rewrite. Facebook, later Meta, ran much of its web code in Hack.",
        example = """
            function greet(string <DOLLAR>name): string {
              return "Hello, " . <DOLLAR>name;
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Web and typed",
        title = "Elm",
        originalTitle = "Elm",
        author = "Evan Czaplicki",
        year = 2012,
        summary = "A functional language for web front ends whose compiler gives helpful messages and avoids runtime exceptions in typical code.",
        details = "Czaplicki released Elm in 2012 as a functional language that compiles to JavaScript. Its architecture keeps the application state in one model " +
            "that changes only through messages. The compiler's error messages became known for being unusually helpful.",
        example = """
            import Html exposing (text)

            main =
              text "Hello, Elm"
        """,
        rare = true,
    ),

    // ---------- Functional and data ----------

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Functional and data",
        title = "Elixir",
        originalTitle = "Elixir",
        author = "José Valim",
        year = 2012,
        summary = "A functional language on the Erlang virtual machine, with Ruby-like syntax and built-in concurrency.",
        details = "Valim released Elixir publicly in May 2012, after working on it since 2011. It runs on the Erlang virtual machine, so it inherits lightweight processes and fault tolerance, " +
            "and it adds macros and a friendlier syntax. The Phoenix web framework, which reached version 1.0 in 2015, made it popular for web services.",
        example = """
            defmodule Greeter do
              def greet(name), do: "Hello, #{name}"
            end

            IO.puts Greeter.greet("Ann")
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Functional and data",
        title = "Julia",
        originalTitle = "Julia",
        author = "Jeff Bezanson, Stefan Karpinski, Viral Shah and Alan Edelman",
        year = 2012,
        summary = "A language for numerical computing with a fast compiler and multiple dispatch.",
        details = "The four creators announced Julia in February 2012, aiming for the speed of C with the ease of Python for technical work. " +
            "Multiple dispatch chooses the method from the types of all its arguments, which lets numerical libraries stay both generic and fast.",
        example = """
            struct Circle
                r::Float64
            end

            area(c::Circle) = π * c.r^2
            println(area(Circle(2.0)))
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Functional and data",
        title = "Idris",
        originalTitle = "Idris 1.0",
        author = "Edwin Brady",
        year = 2017,
        summary = "A dependently typed functional language in which types can describe and prove properties of programs.",
        details = "Brady had been developing Idris as a research language since 2007, and version 1.0 came out in April 2017. A type may mention values, so a function can carry a proof " +
            "that its result has the right length, and the compiler checks that proof before the program runs.",
        example = """
            vappend : Vect n a -> Vect m a -> Vect (n + m) a
            vappend [] ys = ys
            vappend (x :: xs) ys = x :: vappend xs ys
        """,
        rare = true,
    ),

    // ---------- Blockchain and contracts ----------

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Blockchain and contracts",
        title = "Solidity",
        originalTitle = "Solidity",
        author = "Gavin Wood, Christian Reitwiessner and the Ethereum team",
        year = 2015,
        summary = "The language for Ethereum smart contracts: programs stored on a shared blockchain that pay gas for each step.",
        details = "Solidity came with the Ethereum platform in 2015. Contracts are compiled to bytecode for the Ethereum Virtual Machine and run on every node, " +
            "so each operation costs gas. Code on a public ledger cannot be patched casually, so its designers stressed caution, and expensive bugs were found in contracts later.",
        example = """
            pragma solidity ^0.8.0;

            contract Counter {
                uint256 public count;

                function increment() public {
                    count += 1;
                }
            }
        """,
    ),

    // ---------- Standards and revisions ----------

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "C++11",
        originalTitle = "C++11 (ISO/IEC 14882:2011)",
        author = "Bjarne Stroustrup and the ISO C++ committee",
        year = 2011,
        summary = "The large C++ revision with lambdas, move semantics, auto and a standard threads library.",
        details = "ISO standardised C++11 in 2011, the largest revision since the first standard. Lambdas, move semantics, auto, range-based loops " +
            "and the standard thread library changed how C++ code was written, and later standards built on them.",
        instructions = """
            1. Save the example as lambdas.cpp.
            2. Compile it: g++ -std=c++11 -o lambdas lambdas.cpp
            3. Run it: ./lambdas
        """,
        example = """
            #include <algorithm>
            #include <iostream>
            #include <string>
            #include <vector>

            int main() {
                std::vector<std::string> words = {"compiler", "c", "linker"};
                std::sort(words.begin(), words.end(),
                          [](const std::string& a, const std::string& b) { return a.size() < b.size(); });
                for (const auto& word : words)
                    std::cout << word << "\n";
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Java 8",
        originalTitle = "Java SE 8",
        author = "Oracle and the Java Community Process",
        year = 2014,
        summary = "Lambda expressions, the Streams API and default methods, which brought functional-style code to Java.",
        details = "Java 8 (March 2014) added lambda expressions, the Streams API and default methods in interfaces. " +
            "Collections could be processed in a pipeline of map, filter and reduce steps, which made many loops shorter and easier to run in parallel.",
        instructions = """
            1. Save the example as Lambdas.java.
            2. Compile it: javac Lambdas.java
            3. Run it: java Lambdas
        """,
        example = """
            public class Lambdas {
                interface Greeter {
                    String greet(String name);

                    default String greetTwice(String name) {
                        return greet(name) + " " + greet(name);
                    }
                }

                public static void main(String[] args) {
                    Runnable hello = () -> System.out.println("a lambda");
                    hello.run();
                    Greeter polite = name -> "Hello, " + name;
                    System.out.println(polite.greetTwice("Ann"));
                }
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "Python 3.6",
        originalTitle = "Python 3.6",
        author = "Python Software Foundation and the core developers",
        year = 2016,
        summary = "Variable annotations for type checkers and formatted string literals, which made Python code clearer and checkable.",
        details = "Python 3.6 (December 2016) added variable annotations for type checkers and f-strings for quick formatting. " +
            "Python 2 was still common in 2016, and these additions became part of everyday Python style.",
        instructions = """
            1. Save the example as annotated.py.
            2. Run it: python3 annotated.py
        """,
        example = """
            from typing import List

            def total(prices: List[float]) -> float:
                return sum(prices)

            prices: List[float] = [2.0, 3.5]
            print(total(prices))
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Standards and revisions",
        title = "JavaScript ES2015",
        originalTitle = "ECMAScript 2015 (ES6)",
        author = "Ecma International (TC39)",
        year = 2015,
        summary = "The sixth edition of ECMAScript: classes, arrow functions, modules, promises, and let and const.",
        details = "ECMAScript 2015, known as ES6, was published in June 2015. It added classes, arrow functions, template literals, modules, promises, " +
            "and block-scoped let and const. Browsers and build tools spent several years catching up, and transpilers let teams use the new syntax early.",
        instructions = """
            1. Save the example as counter.js.
            2. Run it: node counter.js
        """,
        example = """
            class Counter {
              constructor() { this.count = 0; }
              increment() { this.count += 1; return this; }
            }
            console.log(new Counter().increment().increment().count);
        """,
    ),

    // ---------- Education and esoteric ----------

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Education and esoteric",
        title = "Scratch 2.0",
        originalTitle = "Scratch 2.0",
        author = "Mitchel Resnick and the MIT Media Lab",
        year = 2013,
        summary = "A block-based language for children, with a web version in which projects and sprites can be shared.",
        details = "The MIT Media Lab released Scratch 2.0 in 2013 as a browser-based environment. Children snap blocks together to make animations and games, " +
            "and the community shares projects online. The design builds on earlier educational languages such as Logo.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.LANGUAGE,
        section = "Education and esoteric",
        title = "Hexagony",
        originalTitle = "Hexagony",
        author = "Martin Büttner (known online as Martin Ender)",
        year = 2015,
        summary = "An esoteric language whose program sits on a hexagonal grid, and whose instruction pointer moves diagonally through it.",
        details = "Büttner created Hexagony in 2015. The source code is laid out as a hexagon, and the instruction pointer moves across its edges, " +
            "so the shape of the code matters as much as its text. Its memory is a separate hexagonal grid as well, which makes each program a puzzle in its own right.",
        rare = true,
    ),
)
