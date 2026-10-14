package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex projects of the 1990s: showpieces that squeeze the most out of a few lines of code. */
val db2Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 2,
        kind = Kind.FLEX,
        section = "Graphics and maths",
        title = "Mandelbrot set in plain JavaScript",
        originalTitle = "Mandelbrot in ASCII",
        author = "Demo-style showpiece",
        year = 1995,
        summary = "About twenty lines of JavaScript draw the Mandelbrot set in the console, as ASCII.",
        details = "The loop iterates z = z * z + c for each character cell. A cell gets a dot, a plus or a hash, depending on how long the value stays bounded. " +
            "The code uses only the basic features that 1990s browsers supported, so it would have run in a page of the decade.",
        instructions = """
            1. Save the code as mandel.js.
            2. Run it: node mandel.js
            3. Make the picture larger or move it by changing the numbers in x / 30 and y / 12.
        """,
        example = """
            for (var y = -12; y <= 12; y++) {
                var line = "";
                for (var x = -39; x <= 20; x++) {
                    var cr = x / 30, ci = y / 12;
                    var zr = 0, zi = 0, n = 0;
                    while (n < 50 && zr * zr + zi * zi < 4) {
                        var t = zr * zr - zi * zi + cr;
                        zi = 2 * zr * zi + ci;
                        zr = t;
                        n++;
                    }
                    line += n == 50 ? "#" : (n > 5 ? "+" : ".");
                }
                console.log(line);
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.FLEX,
        section = "Tricks",
        title = "Perl quine",
        originalTitle = "Quine in Perl",
        author = "Quine tradition",
        year = 1995,
        summary = "A Perl program that prints its own source code, character for character.",
        details = "A quine is a program whose output is its own text. The string holds a template with three placeholders; " +
            "printf fills them with the apostrophe character (code 39) and with the template itself. The output is the source, exactly.",
        instructions = """
            1. Save the line exactly as shown, without adding a newline at the end, as quine.pl.
            2. Run it and send the output to a new file: perl quine.pl > copy.pl
            3. The two files are identical: cmp quine.pl copy.pl prints nothing.
        """,
        example = """
            <DOLLAR>_='<DOLLAR>_=%c%s%c;printf(<DOLLAR>_,39,<DOLLAR>_,39);';printf(<DOLLAR>_,39,<DOLLAR>_,39);
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.FLEX,
        section = "Templates",
        title = "Primes decided at compile time (C++98)",
        originalTitle = "Template metaprogramming",
        author = "C++ template community",
        year = 1995,
        summary = "Primality is computed by the compiler with recursive templates; the program only prints the answers.",
        details = "A template with two integer parameters recurses over the possible divisors and stops at 1. " +
            "The result of each test is an enumerator, so the whole computation happens during compilation. " +
            "This was a showpiece in the mid-90s, when template recursion was new to most programmers.",
        instructions = """
            1. Save the code as primes.cpp.
            2. Compile it in C++98 mode: g++ -std=c++98 -o primes primes.cpp
            3. Run ./primes: the lines show 2 and 29 as prime and 9 as not prime.
        """,
        example = """
            #include <iostream>

            template <int N, int D>
            struct NoDivisor {
                enum { value = (N % D != 0) && NoDivisor<N, D - 1>::value };
            };

            template <int N>
            struct NoDivisor<N, 1> {
                enum { value = 1 };
            };

            template <int N>
            struct IsPrime {
                enum { value = NoDivisor<N, N - 1>::value };
            };

            int main() {
                std::cout << "2 prime: " << IsPrime<2>::value << "\n";
                std::cout << "9 prime: " << IsPrime<9>::value << "\n";
                std::cout << "29 prime: " << IsPrime<29>::value << "\n";
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.FLEX,
        section = "Graphics and maths",
        title = "ASCII plasma in C",
        originalTitle = "Plasma effect",
        author = "Demoscene practice",
        year = 1993,
        summary = "Three sine waves added together give a height, and each height is printed as one of ten ASCII shades.",
        details = "Plasma was a favourite demoscene effect: the colour of each point depends on the sum of sine waves over the screen. " +
            "Here the value is printed as a shade character instead of a colour. Changing the frequencies of the waves changes the pattern.",
        instructions = """
            1. Save the code as plasma.c.
            2. Compile it: cc -Wall -o plasma plasma.c -lm
            3. Run ./plasma: it prints 20 lines of 60 characters.
        """,
        example = """
            #include <math.h>
            #include <stdio.h>

            int main(void)
            {
                const char *shades = " .:-=+*#%@";
                int x, y;
                for (y = 0; y < 20; y++) {
                    for (x = 0; x < 60; x++) {
                        double v = sin(x / 6.0) + sin(y / 3.0) + sin((x + y) / 6.0);
                        int level = (int)((v + 3.0) / 6.0 * 9.99);
                        putchar(shades[level]);
                    }
                    putchar('\n');
                }
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.FLEX,
        section = "Graphics and maths",
        title = "Fire effect in awk",
        originalTitle = "Fire effect",
        author = "Demoscene practice",
        year = 1994,
        summary = "Heat rises from the bottom row, cools a little at each step and is printed with ten characters.",
        details = "Each cell takes the average of the three cells below it and then cools by a random amount. " +
            "The bottom row is kept hot. After a few steps the heat spreads upward and forms the flames.",
        instructions = """
            1. Save the code as fire.awk.
            2. Run it: awk -f fire.awk
            3. Change the number of steps in the frame loop to make the flames taller.
        """,
        example = """
            BEGIN {
                srand(7)
                W = 40; H = 12
                palette = " .:-=+*#%@"
                for (frame = 0; frame < 20; frame++) {
                    for (x = 1; x <= W; x++) heat[H, x] = 9     # the bottom row is always hot
                    for (y = 1; y < H; y++)
                        for (x = 1; x <= W; x++) {
                            l = (x > 1) ? x - 1 : 1
                            r = (x < W) ? x + 1 : W
                            v = (heat[y + 1, x] + heat[y + 1, l] + heat[y + 1, r]) / 3
                            v -= rand() * 0.6
                            heat[y, x] = (v < 0) ? 0 : v
                        }
                }
                for (y = 1; y <= H; y++) {
                    line = ""
                    for (x = 1; x <= W; x++)
                        line = line substr(palette, int(heat[y, x]) + 1, 1)
                    print line
                }
            }
        """,
    ),
)
