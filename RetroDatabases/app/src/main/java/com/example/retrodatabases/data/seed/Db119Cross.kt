package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 850 to 860, a placed record: the sphere and cylinder ratio of the Banu Musa's treatise, counted in Python and in C. */
val db119Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.CROSS,
        section = "Geometry",
        title = "The sphere and cylinder ratio of the Banu Musa's treatise, in Python and C",
        originalTitle = "The sphere and cylinder ratio of the Banu Musa's treatise, in Python and C",
        author = "Archimedes and the Banu Musa brothers",
        year = 850,
        summary = "Python and C programs check that a sphere has two thirds of the volume and of the surface of its circumscribing cylinder, and a script confirms that both print the same result.",
        details = "This is a placed record: the sources date the Banu Musa treatise on plane and spherical figures only to the ninth century, so the year 850 is the anchor of the brothers' Book of Ingenious Devices and not a date for the treatise. Archimedes proved the two thirds ratio in the third century BC; the treatise takes up the volumes of spheres and cylinders; and the programs use whole numbers, so both languages give the same totals.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sphere.py
            RADIUS = 3
            sphere_volume = 4 * RADIUS ** 3 // 3
            cylinder_volume = 2 * RADIUS ** 3
            sphere_surface = 4 * RADIUS ** 2
            cylinder_surface = 6 * RADIUS ** 2
            print("sphere volume:", sphere_volume, "pi")
            print("cylinder volume:", cylinder_volume, "pi")
            print("sphere surface:", sphere_surface, "pi")
            print("cylinder surface:", cylinder_surface, "pi")
            print("same ratio:", sphere_volume * 3 == cylinder_volume * 2 and sphere_surface * 3 == cylinder_surface * 2)
            ### sphere.c
            #include <stdio.h>

            int main(void) {
                int r = 3;
                int sphere_volume = 4 * r * r * r / 3;
                int cylinder_volume = 2 * r * r * r;
                int sphere_surface = 4 * r * r;
                int cylinder_surface = 6 * r * r;
                int same = sphere_volume * 3 == cylinder_volume * 2 && sphere_surface * 3 == cylinder_surface * 2;
                printf("sphere volume: %d pi\n", sphere_volume);
                printf("cylinder volume: %d pi\n", cylinder_volume);
                printf("sphere surface: %d pi\n", sphere_surface);
                printf("cylinder surface: %d pi\n", cylinder_surface);
                printf("same ratio: %s\n", same ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 sphere.py > python.txt
            cc -o sphere_c sphere.c
            ./sphere_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
