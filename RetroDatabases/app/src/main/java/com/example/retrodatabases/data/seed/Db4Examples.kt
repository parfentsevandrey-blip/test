package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Small examples of 2010 to 2020: one feature per program, short enough to read in a minute. */
val db4Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.EXAMPLE,
        section = "Python",
        title = "async and await in Python",
        originalTitle = "PEP 492 (Python 3.5)",
        author = "Yury Selivanov and the Python core developers",
        year = 2015,
        summary = "Two coroutines that wait on timers at the same time, so the whole program takes as long as the slower one.",
        details = "Python 3.5 added the async and await keywords for coroutines, following PEP 492. asyncio.run, which starts the event loop in one call, came in Python 3.7. " +
            "The example starts two coroutines and gathers their results in the order they were given.",
        instructions = """
            1. Save the example as async_demo.py.
            2. Run it: python3 async_demo.py
        """,
        example = """
            import asyncio

            async def fetch(name, delay):
                await asyncio.sleep(delay)
                return name + " done"

            async def main():
                results = await asyncio.gather(fetch("a", 0.2), fetch("b", 0.1))
                for line in results:
                    print(line)

            asyncio.run(main())
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.EXAMPLE,
        section = "Python",
        title = "Dataclasses in Python",
        originalTitle = "PEP 557 (Python 3.7)",
        author = "Eric V. Smith and the Python core developers",
        year = 2018,
        summary = "A class decorator that writes the constructor, the printed form and the equality check from the field list.",
        details = "Python 3.7 added the dataclasses module in 2018, following PEP 557. The decorator reads the annotated fields of a class " +
            "and generates the methods that a plain class would need written by hand.",
        instructions = """
            1. Save the example as point.py.
            2. Run it: python3 point.py
        """,
        example = """
            from dataclasses import dataclass

            @dataclass
            class Point:
                x: int
                y: int = 0

            p = Point(3)
            print(p)
            print(p == Point(3, 0))
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.EXAMPLE,
        section = "Java",
        title = "The var keyword in Java",
        originalTitle = "Local variable type inference (Java 10)",
        author = "Oracle and the OpenJDK community",
        year = 2018,
        summary = "Local variables whose type the compiler infers from the value, so the declaration does not repeat the type.",
        details = "Java 10 shipped in March 2018 with var for local variables. The compiler works out the type from the initialiser, so loops and generic collections need less typing. " +
            "The type is still fixed at compile time, and var can be used only for locals.",
        instructions = """
            1. Save the example as VarDemo.java.
            2. Compile it: javac VarDemo.java
            3. Run it: java VarDemo
        """,
        example = """
            import java.util.ArrayList;

            public class VarDemo {
                public static void main(String[] args) {
                    var names = new ArrayList<String>();
                    names.add("Ann");
                    names.add("Bob");
                    for (var name : names) {
                        System.out.println("Hello, " + name);
                    }
                }
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.EXAMPLE,
        section = "C++",
        title = "std::optional in C++17",
        originalTitle = "std::optional (C++17)",
        author = "The ISO C++ committee",
        year = 2017,
        summary = "A value that may be missing, so a function can return a name or nothing without a null pointer or a sentinel value.",
        details = "C++17 added std::optional to the standard library. A function that may find nothing returns an optional, and the caller tests it before using the value. " +
            "The example prints a name for one id and a fallback for the other.",
        instructions = """
            1. Save the example as optional.cpp.
            2. Compile it: g++ -std=c++17 -o optional optional.cpp
            3. Run it: ./optional
        """,
        example = """
            #include <iostream>
            #include <optional>
            #include <string>

            std::optional<std::string> find_name(int id) {
                if (id == 1) return std::string("Ann");
                return std::nullopt;
            }

            int main() {
                int ids[] = {1, 2};
                for (int id : ids) {
                    auto name = find_name(id);
                    std::cout << (name ? *name : std::string("nobody")) << "\n";
                }
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.EXAMPLE,
        section = "PHP",
        title = "PHP 7 scalar types and the null coalescing operator",
        originalTitle = "PHP 7.0 features",
        author = "The PHP development team",
        year = 2015,
        summary = "Typed function parameters that refuse wrong types, and a ?? operator that reads a value or falls back to a default.",
        details = "PHP 7.0 was released in December 2015. With declare(strict_types=1), scalar parameters must have the declared type. " +
            "The null coalescing operator ?? returns the left side when it is set and the right side otherwise, which replaces a longer isset check.",
        instructions = """
            1. Save the example as scalars.php.
            2. Run it: php scalars.php
        """,
        example = """
            <?php
            declare(strict_types=1);

            function greet(string <DOLLAR>name, int <DOLLAR>age): string
            {
                return "Hello, " . <DOLLAR>name . " (" . <DOLLAR>age . ")\n";
            }

            echo greet("Ann", 31);
            <DOLLAR>settings = [];
            echo <DOLLAR>settings['mode'] ?? "default", "\n";
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.EXAMPLE,
        section = "Git",
        title = "Two working trees from one Git repository",
        originalTitle = "git worktree",
        author = "The Git project (Git 2.5)",
        year = 2015,
        summary = "One repository with two checked-out folders, so an experiment can run next to the main work without a second clone.",
        details = "git worktree arrived in Git 2.5 in July 2015. A linked worktree is a second folder that shares the same history and object store. " +
            "Each folder has its own branch checked out, so two branches can be built or tested at the same time.",
        instructions = """
            1. Save the commands below as worktree.sh.
            2. Run it: sh worktree.sh
            3. The last lines list both folders and the branch each one has checked out.
        """,
        example = """
            git init -q main-repo
            cd main-repo
            git config user.name "Ann"
            git config user.email "ann@example.com"
            echo one > notes.txt
            git add notes.txt
            git commit -qm "first"
            git worktree add ../second-tree -b experiment
            cd ../second-tree
            echo two >> notes.txt
            git commit -qam "experiment"
            cd ../main-repo
            git worktree list
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.EXAMPLE,
        section = "Go",
        title = "A worker pool with goroutines and channels",
        originalTitle = "Go 1.0 goroutines and channels",
        author = "The Go team",
        year = 2012,
        summary = "Three goroutines take jobs from one channel and send results back on another, while a WaitGroup closes the results at the end.",
        details = "Go 1.0 shipped in March 2012 with goroutines, channels and the sync package. The example is a common pattern for spreading work over a few workers. " +
            "The results arrive in any order, so the example sorts them before printing.",
        instructions = """
            1. Save the example as pool.go.
            2. Run it: go run pool.go
        """,
        example = """
            package main

            import (
                "fmt"
                "sort"
                "sync"
            )

            func main() {
                jobs := make(chan int)
                results := make(chan int)
                var wg sync.WaitGroup
                for w := 1; w <= 3; w++ {
                    wg.Add(1)
                    go func() {
                        defer wg.Done()
                        for job := range jobs {
                            results <- job * job
                        }
                    }()
                }
                go func() {
                    for n := 1; n <= 5; n++ {
                        jobs <- n
                    }
                    close(jobs)
                }()
                go func() {
                    wg.Wait()
                    close(results)
                }()
                var squares []int
                for r := range results {
                    squares = append(squares, r)
                }
                sort.Ints(squares)
                fmt.Println(squares)
            }
        """,
        rare = true,
    ),
)
