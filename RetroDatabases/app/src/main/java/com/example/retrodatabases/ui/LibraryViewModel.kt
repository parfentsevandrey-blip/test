package com.example.retrodatabases.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.retrodatabases.data.AppDatabase
import com.example.retrodatabases.data.DatabaseEntity
import com.example.retrodatabases.data.DatabaseSummary
import com.example.retrodatabases.data.RecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Источник данных для всех экранов: списки баз, записи каталога, избранное. */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).libraryDao()

    val databases: StateFlow<List<DatabaseSummary>> = dao.observeDatabases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun database(databaseId: Long): Flow<DatabaseEntity?> = dao.observeDatabase(databaseId)

    /** Записи базы в порядке каталога: вид, раздел, год, название. */
    fun records(databaseId: Long): Flow<List<RecordEntity>> =
        dao.observeRecords(databaseId).map { records -> records.sortedWith(CatalogOrder) }

    fun record(recordId: Long): Flow<RecordEntity?> = dao.observeRecord(recordId)

    fun toggleFavorite(recordId: Long) {
        viewModelScope.launch { dao.toggleFavorite(recordId) }
    }
}

private val CatalogOrder: Comparator<RecordEntity> =
    compareBy<RecordEntity> { it.kind.ordinal }
        .thenBy { it.section }
        .thenBy { it.year }
        .thenBy { it.title }
