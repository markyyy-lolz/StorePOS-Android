package com.storepos.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class Metric(val label: String, val value: String, val hint: String)
private data class QuickAction(val label: String, val icon: ImageVector)
private data class Job(val number: String, val bike: String, val status: String)

@Composable
fun DashboardScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    TabletSidebar()
                    DashboardContent(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    DashboardContent(
                        modifier = Modifier.weight(1f)
                    )
                    MobileNavigation()
                }
            }
        }
    }
}

@Composable
private fun TabletSidebar() {
    Column(
        modifier = Modifier
            .width(228.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Text(
            text = "MOTO",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "POS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(30.dp))

        SideNavItem("Dashboard", Icons.Rounded.Dashboard, selected = true)
        SideNavItem("Point of Sale", Icons.Rounded.PointOfSale)
        SideNavItem("Service Jobs", Icons.Rounded.Build)
        SideNavItem("Inventory", Icons.Rounded.Inventory2)
        SideNavItem("Motorcycles", Icons.Rounded.TwoWheeler)
        SideNavItem("Customers", Icons.Rounded.Person)

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        Spacer(Modifier.height(12.dp))
        SideNavItem("Settings", Icons.Rounded.Settings)
    }
}

@Composable
private fun SideNavItem(label: String, icon: ImageVector, selected: Boolean = false) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(background, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun DashboardContent(modifier: Modifier = Modifier) {
    val metrics = listOf(
        Metric("Today's Sales", "₱48,540", "+12.8% vs yesterday"),
        Metric("Service Income", "₱10,670", "8 completed jobs"),
        Metric("Gross Profit", "₱13,820", "28.5% margin"),
        Metric("Low Stock", "14", "4 critical items")
    )

    val quickActions = listOf(
        QuickAction("New Sale", Icons.Rounded.AddShoppingCart),
        QuickAction("New Job Order", Icons.Rounded.Build),
        QuickAction("Stock In", Icons.Rounded.Inventory2),
        QuickAction("Motorcycle", Icons.Rounded.TwoWheeler)
    )

    val jobs = listOf(
        Job("JO-1048", "Honda Click 125i V3", "Repairing"),
        Job("JO-1049", "Yamaha NMAX 155", "Testing"),
        Job("JO-1050", "Honda Beat FI", "Waiting Parts")
    )

    LazyColumn(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            TopHeader()
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(metrics) { metric ->
                    MetricCard(metric)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Quick actions")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(quickActions) { action ->
                        QuickActionCard(action)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            SectionTitle("Active service jobs")
                            Text(
                                text = "Live workshop queue",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Text(
                            text = "View all",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    jobs.forEachIndexed { index, job ->
                        JobRow(job)
                        if (index < jobs.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Inventory needs attention",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "14 products are below reorder level. Four are marked critical.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Good evening",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "StorePOS Dashboard",
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        FilledTonalIconButton(onClick = {}) {
            Icon(Icons.Rounded.NotificationsNone, contentDescription = "Notifications")
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = {}) {
            Icon(Icons.Rounded.Person, contentDescription = "Account")
        }
    }
}

@Composable
private fun MetricCard(metric: Metric) {
    Card(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = metric.label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = metric.value,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = metric.hint,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun QuickActionCard(action: QuickAction) {
    Card(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun JobRow(job: Job) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = job.number,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = job.bike,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = job.status,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun MobileNavigation() {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val items = listOf(
            "Home" to Icons.Rounded.Dashboard,
            "POS" to Icons.Rounded.PointOfSale,
            "Service" to Icons.Rounded.Build,
            "Stock" to Icons.Rounded.Inventory2,
            "More" to Icons.Rounded.MoreHoriz
        )

        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == 0,
                onClick = {},
                icon = { Icon(item.second, contentDescription = item.first) },
                label = { Text(item.first) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            )
        }
    }
}
