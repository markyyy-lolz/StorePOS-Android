package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storepos.app.data.model.ShopContext

@Composable
fun RetailOperationsPage(context: ShopContext) {
    var selected by remember(context.shop.id) { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple("Retail Suite", Icons.Rounded.Store, "Stock, promos, reservations & retail tools"),
        Triple("Control Center", Icons.Rounded.AdminPanelSettings, "Approvals, X report, reconciliation & health"),
        Triple("Register Ops", Icons.Rounded.Handyman, "Drawer, shifts, returns, voids & aftercare")
    )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScrollableTabRow(
            selectedTabIndex = selected,
            edgePadding = 0.dp,
            divider = {}
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = {
                        Column {
                            Text(tab.first)
                            Text(
                                tab.third,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    icon = { Icon(tab.second, contentDescription = null) }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                0 -> RetailSuitePage(context)
                1 -> RetailControlPage(context)
                else -> OperationsPage(context)
            }
        }
    }
}
