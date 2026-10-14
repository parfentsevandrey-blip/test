package com.example.retrodatabases.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.retrodatabases.R
import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity
import com.example.retrodatabases.ui.theme.accent

/** Ярлык вида записи («Языки», «Системы» и так далее) на фоне цвета вида. */
@Composable
fun KindBadge(kind: Kind, modifier: Modifier = Modifier) {
    Text(
        text = kind.label.uppercase(),
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(kind.accent().copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Метка для редких и малоизвестных записей. */
@Composable
fun RareBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.rare_badge),
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

/**
 * Карточка записи каталога: цветная полоска вида, ярлыки, название, оригинальное название,
 * краткое описание и кнопка избранного.
 */
@Composable
fun RecordCard(
    record: RecordEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(record.kind.accent()),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(kind = record.kind)
                    if (record.rare) {
                        Spacer(modifier = Modifier.width(6.dp))
                        RareBadge()
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = record.year.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = record.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(
                        if (record.favorite) R.string.favorite_remove else R.string.favorite_add,
                    ),
                    tint = if (record.favorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}
