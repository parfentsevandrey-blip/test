package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 820: al-Khwarizmi's rule for a square and ten roots equal to thirty-nine, completing the square in Python and in C. */
val db122Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.CROSS,
        section = "Algebra",
        title = "Al-Khwarizmi's rule for a square and ten roots equal to thirty-nine, in Python and in C",
        originalTitle = "Al-Khwarizmi's rule for a square and ten roots equal to thirty-nine, in Python and in C",
        author = "Muhammad ibn Musa al-Khwarizmi",
        year = 820,
        summary = "A Python and a C program solve the Algebra's example, a square and ten roots equal to thirty-nine, by completing the square, and the two outputs are compared.",
        details = "The example, x squared plus ten x equal to thirty-nine, has the answer three, and it is one of the worked problems of the Algebra. The method halves the number of roots, squares the half, adds the square to the number and takes the root, and both programs print the same steps and the answer.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### algebra.py
            roots = 10
            number = 39
            half = roots // 2
            square_of_half = half * half
            total = number + square_of_half
            side = 0
            while side * side < total:
                side += 1
            x = side - half
            print("half of the roots:", half)
            print("square of the half:", square_of_half)
            print("side of the square:", side)
            print("x =", x)
            print("check:", x * x + roots * x == number)
            ### algebra.c
            #include <stdio.h>

            int main(void) {
                int roots = 10;
                int number = 39;
                int half = roots / 2;
                int square_of_half = half * half;
                int total = number + square_of_half;
                int side = 0;
                while (side * side < total) {
                    side++;
                }
                int x = side - half;
                printf("half of the roots: %d\n", half);
                printf("square of the half: %d\n", square_of_half);
                printf("side of the square: %d\n", side);
                printf("x = %d\n", x);
                printf("check: %s\n", x * x + roots * x == number ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 algebra.py > python.txt
            cc -o algebra_c algebra.c
            ./algebra_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
