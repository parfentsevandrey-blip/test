package com.example.retrodatabases.data

import androidx.room.TypeConverter

/** Сохраняет вид записи как текст (имя константы enum) и читает обратно. */
class Converters {

    @TypeConverter
    fun kindToString(kind: Kind): String = kind.name

    @TypeConverter
    fun stringToKind(name: String): Kind = Kind.valueOf(name)
}
