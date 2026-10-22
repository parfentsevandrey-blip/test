package io.pocketmind.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.pocketmind.AppContainer
import io.pocketmind.ui.chat.ChatScreen
import io.pocketmind.ui.chat.ChatViewModel
import io.pocketmind.ui.device.DeviceScreen
import io.pocketmind.ui.models.ModelsScreen
import kotlinx.serialization.Serializable

@Serializable data object DeviceRoute : NavKey
@Serializable data object ModelsRoute : NavKey
@Serializable data object ChatRoute : NavKey

private enum class TopLevel(
    val route: NavKey,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Device(DeviceRoute, "Устройство", Icons.Outlined.Memory, Icons.Filled.Memory),
    Models(ModelsRoute, "Модели", Icons.Outlined.ViewInAr, Icons.Filled.ViewInAr),
    Chat(ChatRoute, "Диалог", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
}

/** Top-level tabs keep a single-level stack on top of the start destination. */
private fun NavBackStack<NavKey>.switchTo(route: NavKey) {
    if (lastOrNull() == route) return
    while (size > 1) removeAt(lastIndex)
    if (route != DeviceRoute) add(route)
}

@Composable
fun PocketMindRoot(container: AppContainer) {
    val backStack = rememberNavBackStack(DeviceRoute)
    // Activity-scoped so that the conversation and analysis survive tab switches.
    val home: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
    val chat: ChatViewModel = viewModel(factory = viewModelFactory { initializer { ChatViewModel(container) } })
    val current = backStack.lastOrNull() ?: DeviceRoute

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevel.entries.forEach { tab ->
                val selected = current == tab.route
                item(
                    selected = selected,
                    onClick = { backStack.switchTo(tab.route) },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<DeviceRoute> {
                    DeviceScreen(
                        viewModel = home,
                        onOpenModels = { backStack.switchTo(ModelsRoute) },
                        onOpenChat = { backStack.switchTo(ChatRoute) },
                    )
                }
                entry<ModelsRoute> {
                    ModelsScreen(viewModel = home, onOpenChat = { backStack.switchTo(ChatRoute) })
                }
                entry<ChatRoute> {
                    ChatScreen(viewModel = chat, onOpenModels = { backStack.switchTo(ModelsRoute) })
                }
            },
        )
    }
}
