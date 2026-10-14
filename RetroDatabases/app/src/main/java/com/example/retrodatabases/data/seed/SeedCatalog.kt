package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.DatabaseEntity

/**
 * Каталог баз, которые создаются при первом запуске.
 * Чтобы добавить базу: допишите сюда запись и создайте рядом файл с её записями (databaseId = новый id).
 */
val seedDatabases: List<DatabaseEntity> = listOf(
    DatabaseEntity(
        id = 1,
        name = "База 1: каталог кодинга 80-х",
        description = "Языки, системы, устройства, инструменты и практики 1980-х, включая редкие и малоизвестные.",
        colorHex = "#4DD0E1",
    ),
)
