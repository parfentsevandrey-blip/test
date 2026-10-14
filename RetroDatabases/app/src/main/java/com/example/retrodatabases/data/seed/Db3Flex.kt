package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex projects of 2000 to 2010: showpieces that squeeze the most out of a few lines of code. */
val db3Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 3,
        kind = Kind.FLEX,
        section = "Puzzles and search",
        title = "Sudoku solver in thirty lines of Python",
        originalTitle = "Backtracking Sudoku solver",
        author = "Inspired by Peter Norvig's 2006 essay on solving Sudoku",
        year = 2006,
        summary = "A backtracking solver that fills the grid cell by cell and tries only the digits that are still allowed.",
        details = "The solver walks the grid in reading order. For each empty cell it collects the digits not yet used in the row, the column and the 3 by 3 box, " +
            "and tries them one by one. When a cell has no allowed digit, the function gives up and the previous cell tries its next digit. " +
            "A classic puzzle is solved in a fraction of a second.",
        instructions = """
            1. Save the code as sudoku.py.
            2. Run it: python3 sudoku.py
            3. The output is the solved grid, nine lines of nine digits.
        """,
        example = """
            # A compact Sudoku solver: backtracking, trying only the digits that are still allowed.
            puzzle = (
                "53..7...."
                "6..195..."
                ".98....6."
                "8...6...3"
                "4..8.3..1"
                "7...2...6"
                ".6....28."
                "...419..5"
                "....8..79"
            )

            def allowed(grid, row, col):
                used = set(grid[row]) | set(grid[r][col] for r in range(9))
                top, left = 3 * (row // 3), 3 * (col // 3)
                used |= set(grid[r][c] for r in range(top, top + 3) for c in range(left, left + 3))
                return [d for d in "123456789" if d not in used]

            def solve(grid):
                for row in range(9):
                    for col in range(9):
                        if grid[row][col] == ".":
                            for digit in allowed(grid, row, col):
                                grid[row][col] = digit
                                if solve(grid):
                                    return True
                            grid[row][col] = "."
                            return False
                return True

            grid = [list(puzzle[i * 9:(i + 1) * 9]) for i in range(9)]
            solve(grid)
            for row in grid:
                print("".join(row))
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.FLEX,
        section = "Tricks",
        title = "Ruby metaprogramming: methods made at run time",
        originalTitle = "define_method",
        author = "Ruby community practice",
        year = 2006,
        summary = "A class that defines its own getters and setters with a loop, so each field needs only one line of code.",
        details = "Ruby lets a program define methods while it runs. A loop over the field names calls define_method twice for each name, " +
            "once for the reader and once for the writer. The class has no hand-written accessors, yet its methods behave like any others.",
        instructions = """
            1. Save the code as person.rb.
            2. Run it: ruby person.rb
        """,
        example = """
            class Person
              %w[name age city].each do |field|
                define_method(field) { instance_variable_get("@#{field}") }
                define_method("#{field}=") { |value| instance_variable_set("@#{field}", value) }
              end
            end

            person = Person.new
            person.name = "Ann"
            person.age = 31
            puts "#{person.name} is #{person.age}"
            p Person.instance_methods(false).sort
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.FLEX,
        section = "Tricks",
        title = "One-line Git statistics",
        originalTitle = "git shortlog",
        author = "Git practice",
        year = 2005,
        summary = "The number of commits per author, from one command that reads the repository history.",
        details = "Git stores the author of each commit, so the history can be summarised. The shortlog command groups commits by author and counts them, " +
            "and the -s and -n switches print the counts sorted from the most active author. Teams used it to see who had written most of a project.",
        instructions = """
            1. Save the lines below as stats.sh.
            2. Run it: sh stats.sh
        """,
        example = """
            git init stats
            cd stats
            git config user.name "Ann"
            git config user.email "ann@example.com"
            echo one > a.txt && git add a.txt && git commit -qm "one"
            echo two >> a.txt && git commit -qam "two"
            git config user.name "Bob"
            echo three >> a.txt && git commit -qam "three"
            git shortlog -sn HEAD
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.FLEX,
        section = "Demoscene in JavaScript",
        title = "A 1 KB JavaScript demo",
        originalTitle = "1 KB demo (js1k style)",
        author = "Demo-contest practice",
        year = 2010,
        summary = "A dot that swings like a sine wave, drawn one line per frame and short enough for a 1 KB contest entry.",
        details = "Contests that asked for demos in 1024 bytes of JavaScript appeared in 2010. Entries had to be tiny, so authors used short names, " +
            "compact loops and one expression per frame. This demo draws a dot that swings left and right, one line for each frame.",
        instructions = """
            1. Save the code as wave.js.
            2. Run it: node wave.js
        """,
        example = """
            // A dot that swings like a sine wave: one line per frame, 40 columns wide
            for (var t = 0; t < 20; t++) {
              var pos = Math.round(20 + 18 * Math.sin(t / 3));
              var row = "";
              for (var x = 0; x < 40; x++) row += x == pos ? "*" : ".";
              console.log(row);
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.FLEX,
        section = "Web servers",
        title = "Apache mod_rewrite: moving a whole site with one rule",
        originalTitle = "RewriteRule with a back-reference",
        author = "Apache mod_rewrite practice",
        year = 2003,
        summary = "One regular-expression rule redirects every old address to the matching new address and keeps the rest of the path.",
        details = "mod_rewrite let a web server rewrite addresses with regular expressions. A rule with a captured group copied the rest of the path " +
            "into the new address. With the redirect flag, browsers and search engines learned the new addresses.",
        instructions = """
            1. Put the rule in the .htaccess file of the old site.
            2. Make sure the server has the rewrite engine enabled.
            3. Request an old address and check that the browser is redirected.
        """,
        example = """
            # .htaccess in the folder of the old site
            RewriteEngine On
            RewriteRule ^old/(.*)$ http://www.example.com/new/<DOLLAR>1 [R=301,L]
        """,
    ),
)
