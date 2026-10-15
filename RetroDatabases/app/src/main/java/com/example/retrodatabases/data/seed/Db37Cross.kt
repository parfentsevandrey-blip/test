package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1670 to 1680: Leibniz's characteristic numbers and Hooke's spring stretch, each in Python and C. */
val db37Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.CROSS,
        section = "Logic",
        title = "Leibniz's characteristic numbers in Python and C",
        originalTitle = "Characteristica universalis",
        author = "Gottfried Wilhelm Leibniz",
        year = 1679,
        summary = "Concepts are given numbers built from primes, and a concept includes another when the other's number is a multiple of its own, so a script checks this in Python and in C.",
        details = "Leibniz's plan for a universal characteristic used products of primes to stand for combinations of concepts, and the divisibility test is the arithmetic side of that plan. The two programs print the same four results, and the run script compares their output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### concepts.py
            NUMBERS = {"animal": 2, "rational": 3, "human": 6, "stone": 7}

            def includes(general, specific):
                # The general concept's number divides the specific concept's number: human is animal times rational.
                return NUMBERS[specific] % NUMBERS[general] == 0

            pairs = [("animal", "human"), ("human", "animal"), ("rational", "stone"), ("animal", "stone")]
            for general, specific in pairs:
                print(f"{general} includes {specific}: {includes(general, specific)}")
            ### concepts.c
            #include <stdio.h>

            /* The general number must divide the specific number: human is animal times rational. */
            static int includes(int general, int specific) {
                return specific % general == 0;
            }

            int main(void) {
                printf("animal includes human: %s\n", includes(2, 6) ? "True" : "False");
                printf("human includes animal: %s\n", includes(6, 2) ? "True" : "False");
                printf("rational includes stone: %s\n", includes(3, 7) ? "True" : "False");
                printf("animal includes stone: %s\n", includes(2, 7) ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 concepts.py > python.txt
            cc -o concepts_c concepts.c
            ./concepts_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.CROSS,
        section = "Mechanics",
        title = "Hooke's spring stretch in Python and C",
        originalTitle = "Lectures de potentia restitutiva",
        author = "Robert Hooke",
        year = 1678,
        summary = "The stretch of a spring under three forces is computed in Python and in C, and a script checks that both programs print the same figures.",
        details = "Hooke's law of 1678 makes the stretch proportional to the force, and the two programs divide each force by the stiffness in the same way. The run script compares the printed lines, so the same figures appear in both languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### hooke_spring.py
            def stretch(force, stiffness):
                # Hooke's law: the stretch is the force divided by the stiffness.
                return force / stiffness

            for force, stiffness in ((3, 4), (5, 2), (9, 4)):
                print(f"force {force} stiffness {stiffness}: stretch {stretch(force, stiffness):.2f}")
            ### hooke_spring.c
            #include <stdio.h>

            /* Hooke's law: the stretch is the force divided by the stiffness. */
            static double stretch(int force, int stiffness) {
                return (double)force / stiffness;
            }

            int main(void) {
                int forces[3] = {3, 5, 9};
                int stiffnesses[3] = {4, 2, 4};
                for (int i = 0; i < 3; i++) {
                    printf("force %d stiffness %d: stretch %.2f\n", forces[i], stiffnesses[i], stretch(forces[i], stiffnesses[i]));
                }
                return 0;
            }
            ### run.sh
            python3 hooke_spring.py > python.txt
            cc -o hooke_spring_c hooke_spring.c
            ./hooke_spring_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
