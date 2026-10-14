package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Flex projects: the maximum is squeezed out of minimal code, just to show what is possible.
 * Each record shows which trick is pushed to the limit and why it works.
 */
val db1Flex: List<RecordEntity> = listOf(

    entry(
        kind = Kind.FLEX,
        section = "Text graphics",
        title = "Sierpinski triangle from one bitwise AND",
        originalTitle = "Sierpinski pattern with x & y",
        author = "Demoscene practice and C books",
        year = 1985,
        summary = "A single bitwise operator draws a fractal: an asterisk goes where the bits of the coordinates do not overlap.",
        details = "The condition (x & y) == 0 gives a pattern similar to the Sierpinski triangle: these are Pascal's triangle cells modulo 2. " +
            "The program has no geometric formulas at all, only a loop over the coordinates and a bitwise AND. " +
            "One-liners like this were a favorite in the demoscene and in C books.",
        instructions = """
            1. Save the code as sierpinski.c and build it: cc -o sierpinski sierpinski.c
            2. Run ./sierpinski — the screen shows 32 lines of the fractal.
            3. To see more levels, replace both 32s with 64.
        """,
        example = """
            #include <stdio.h>

            int main(void)
            {
                int x, y;

                for (y = 0; y < 32; y++) {
                    for (x = 0; x < 32; x++)
                        putchar((x & y) ? ' ' : '*');
                    putchar('\n');
                }
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.FLEX,
        section = "Self-reproduction",
        title = "Quine: a program that prints its own code",
        originalTitle = "C quine",
        author = "Classic C quines, 1980s",
        year = 1984,
        summary = "A program with no input prints its own source text exactly.",
        details = "The string holds a template of the whole program. printf puts the program's own text into the template, and the numbers 34 are the codes of the double quote, so the quote does not need escaping. " +
            "This is how the program knows itself through its own string. " +
            "The same trick lies behind self-reproducing compilers, which Ken Thompson described in his lecture “Reflections on Trusting Trust” (1984).",
        instructions = """
            1. Save the program's line to a file quine.c (with a newline at the end).
            2. Build in the old C mode: cc -std=gnu89 -w -o quine quine.c
            3. Run ./quine > out.c and compare: diff quine.c out.c. The only difference is the newline at the end of the file.
        """,
        example = """
            main(){char*c="main(){char*c=%c%s%c;printf(c,34,c,34);}";printf(c,34,c,34);}
        """,
        rare = true,
    ),

    entry(
        kind = Kind.FLEX,
        section = "Text graphics",
        title = "Mandelbrot set in awk",
        originalTitle = "Mandelbrot set in awk",
        author = "Benoit Mandelbrot (fractals, 1980)",
        year = 1980,
        summary = "A fractal in the terminal: floating-point arithmetic and a character scale right inside a scripting language.",
        details = "For every point c of the plane we repeat z = z² + c and check whether |z| leaves the circle of radius 2. " +
            "If it has not left within the given number of steps, the point belongs to the set and is printed as “#”. " +
            "The other characters show how fast the point escaped. " +
            "The whole “renderer” is two nested loops and a string scale.",
        instructions = """
            1. Save the code as mandel.awk.
            2. Run: awk -f mandel.awk
            3. To zoom in on a fragment, reduce the ranges of x and y, keeping the step.
        """,
        example = """
            BEGIN {
                ramp = " .:-=+*"                  # characters by escape speed
                for (y = -1.2; y <= 1.2; y += 0.1) {
                    line = ""
                    for (x = -2.1; x <= 0.6; x += 0.04) {
                        zr = 0; zi = 0; n = 0
                        while (zr * zr + zi * zi < 4 && n < 30) {
                            t = zr * zr - zi * zi + x
                            zi = 2 * zr * zi + y
                            zr = t
                            n++
                        }
                        if (n == 30)
                            c = "#"
                        else
                            c = substr(ramp, int(n / 5) + 1, 1)
                        line = line c
                    }
                    print line
                }
            }
        """,
    ),

    entry(
        kind = Kind.FLEX,
        section = "One-liners",
        title = "Run-length compression in one awk line",
        originalTitle = "Run-length encoding in one awk line",
        author = "Compression practice, 1980s",
        year = 1985,
        summary = "Repeated characters are written as “count and character”; all the logic fits on one awk line.",
        details = "The run-length encoding scheme was used in graphics formats of the 80s, for example in PCX (1985). " +
            "The outer loop moves along the string, and the inner one counts the length of a run. " +
            "The reverse operation, decompression, is an exercise for the reader.",
        instructions = """
            1. Run the command from the example.
            2. For the string aaabccdddd the result is 3a1b2c4d.
            3. Try a string without repeats, for example abcdef: the result is longer than the input. That is why RLE only helps on repeated data.
        """,
        example = """
            echo aaabccdddd | awk '{ s = $0; o = ""; i = 1; while (i <= length(s)) { c = substr(s, i, 1); r = 1; while (substr(s, i + r, 1) == c) r++; o = o r c; i += r } print o }'
        """,
        rare = true,
    ),

    entry(
        kind = Kind.FLEX,
        section = "One-liners",
        title = "Letter frequencies with a Unix pipeline",
        originalTitle = "Letter frequency pipeline",
        author = "Cryptanalysis practice, 1980s",
        year = 1980,
        summary = "Five Unix tools count letter frequencies in a text: the basis of frequency analysis in cryptanalysis.",
        details = "tr removes everything except Latin letters and lowercases them. fold -w1 splits the text into one character per line, " +
            "sort and uniq -c count the repeats, and sort -rn puts the most frequent letters on top. " +
            "In English text E, T and A lead the list. Frequency tables like this one are used to find the key letters of a cipher.",
        instructions = """
            1. Save an English text as text.txt.
            2. Run the command from the example.
            3. The output shows the count and the letter. Compare them with the letter-frequency table for the language.
        """,
        example = """
            tr -cd 'A-Za-z' < text.txt | tr 'A-Z' 'a-z' | fold -w1 | sort | uniq -c | sort -rn | head -5
        """,
    ),
)
