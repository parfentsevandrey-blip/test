package com.example.retrodatabases.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.retrodatabases.R
import com.example.retrodatabases.data.RecordEntity
import com.example.retrodatabases.ui.LibraryViewModel
import com.example.retrodatabases.ui.components.KindBadge
import com.example.retrodatabases.ui.components.RareBadge

/** Экран одной записи каталога: год, вид, раздел, пояснение, инструкция и пример кода. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val record by remember(recordId) { viewModel.record(recordId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val current = record

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = current?.section.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { viewModel.toggleFavorite(current.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(
                                    if (current.favorite) R.string.favorite_remove else R.string.favorite_add,
                                ),
                                tint = if (current.favorite) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (current != null) {
            RecordDetailContent(record = current, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun RecordDetailContent(record: RecordEntity, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${stringResource(R.string.label_year)} ${record.year}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindBadge(kind = record.kind)
                if (record.rare) {
                    RareBadge()
                }
            }
            Text(
                text = record.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = record.originalTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${stringResource(R.string.label_author)}: ${record.author}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        InfoSection(title = stringResource(R.string.section_summary)) {
            Text(text = record.summary, style = MaterialTheme.typography.bodyLarge)
        }
        InfoSection(title = stringResource(R.string.section_details)) {
            Text(text = record.details, style = MaterialTheme.typography.bodyMedium)
        }
        record.instructions?.let { instructions ->
            InfoSection(title = stringResource(R.string.section_instructions)) {
                Text(text = instructions, style = MaterialTheme.typography.bodyMedium)
            }
        }
        record.example?.let { example ->
            InfoSection(title = stringResource(R.string.section_example)) {
                CodeBlock(code = example)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/** Моноширинный блок кода; длинные строки прокручиваются по горизонтали. */
@Composable
private fun CodeBlock(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            text = code,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
        )
    }
}
