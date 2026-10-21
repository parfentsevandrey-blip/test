package com.example.retrodatabases.data

/**
 * Kind of record: the top level of the catalogue.
 * The database stores the constant name (for example, LANGUAGE), and [label] is shown to the user.
 * The order of the constants sets the order of the sections in the catalogue.
 */
enum class Kind(val label: String) {
    LANGUAGE("Languages"),
    SYSTEM("Systems"),
    DEVICE("Devices"),
    TOOL("Tools"),
    PRACTICE("Practices"),
    GUIDE("Guides"),
    EXAMPLE("Examples"),
    PROJECT("Projects"),
    FLEX("Flex"),
    CROSS("Cross-code"),
    MODEL("Economic models"),
    COMPROMISE("Compromises"),
}
