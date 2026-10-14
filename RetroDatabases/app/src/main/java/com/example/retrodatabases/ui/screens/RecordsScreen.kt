package com.example.retrodatabases.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.retrodatabases.R
import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.ui.LibraryViewModel
import com.example.retrodatabases.ui.components.RecordCard
import com.example.retrodatabases.ui.filterRecords
import com.example.retrodatabases.ui.theme.accent

/**
 * Каталог одной базы: поиск, фильтры по виду, избранному и редким записям,
 * записи сгруппированы по разделам внутри вида.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    databaseId: Long,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val database by remember(databaseId) { viewModel.database(databaseId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val records by remember(databaseId) { viewModel.records(databaseId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var kind by remember { mutableStateOf<Kind?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var rareOnly by remember { mutableStateOf(false) }

    val countByKind = remember(records) { records.groupingBy { it.kind }.eachCount() }
    val visible = remember(records, query, kind, favoritesOnly, rareOnly) {
        filterRecords(records, query, kind, favoritesOnly, rareOnly)
    }
    // Порядок групп сохраняется: записи уже отсортированы по виду и разделу
    val groups = remember(visible) { visible.groupBy { it.kind to it.section } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = database?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_clear),
                            )
                        }
                    }
                },
            )

            LazyRow(
                modifier = Modifier.padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = kind == null && !favoritesOnly && !rareOnly,
                        onClick = {
                            kind = null
                            favoritesOnly = false
                            rareOnly = false
                        },
                        label = { Text("${stringResource(R.string.filter_all)} ${records.size}") },
                    )
                }
                items(Kind.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = if (kind == option) null else option },
                        label = { Text("${option.label} ${countByKind[option] ?: 0}") },
                    )
                }
                item {
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text(stringResource(R.string.filter_favorites)) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    )
                }
                item {
                    FilterChip(
                        selected = rareOnly,
                        onClick = { rareOnly = !rareOnly },
                        label = { Text("${stringResource(R.string.filter_rare)} ${records.count { it.rare }}") },
                    )
                }
            }

            Text(
                text = stringResource(R.string.found_count, visible.size),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.nothing_found),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groups.forEach { (group, sectionRecords) ->
                        val (groupKind, groupSection) = group
                        item(key = "header-${groupKind.name}-$groupSection") {
                            SectionHeader(
                                title = groupSection,
                                count = sectionRecords.size,
                                accent = groupKind.accent(),
                            )
                        }
                        items(sectionRecords, key = { it.id }) { record ->
                            RecordCard(
                                record = record,
                                onClick = { onOpenRecord(record.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(record.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Заголовок раздела внутри вида: цветная черта, название и число записей. */
@Composable
private fun SectionHeader(title: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
