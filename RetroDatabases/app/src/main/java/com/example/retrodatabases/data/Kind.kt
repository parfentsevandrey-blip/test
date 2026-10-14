package com.example.retrodatabases.data

/**
 * Вид записи — верхний уровень каталога.
 * В базе хранится имя константы (например, LANGUAGE), а [label] показывается пользователю.
 * Порядок констант задаёт порядок разделов в каталоге.
 */
enum class Kind(val label: String) {
    LANGUAGE("Языки"),
    SYSTEM("Системы"),
    DEVICE("Устройства"),
    TOOL("Инструменты"),
    PRACTICE("Практики"),
    GUIDE("Пояснения"),
    EXAMPLE("Примеры"),
    PROJECT("Проекты"),
    FLEX("Флекс"),
    CROSS("Кросс-код"),
}
