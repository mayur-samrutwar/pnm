package com.pnm.mobileapp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val isQRButton: Boolean = false
) {
    object Home : BottomNavItem("home", Icons.Rounded.Home, "Home")
    object History : BottomNavItem("history", Icons.Rounded.History, "History")
    object QR : BottomNavItem("qr", Icons.Rounded.QrCodeScanner, "", isQRButton = true)
    object Profile : BottomNavItem("profile", Icons.Rounded.Person, "Profile")
    object Settings : BottomNavItem("settings", Icons.Rounded.Settings, "Settings")
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onPayClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home
            BottomNavButton(
                item = BottomNavItem.Home,
                isSelected = currentRoute == BottomNavItem.Home.route,
                onClick = { onNavigate(BottomNavItem.Home.route) }
            )

            // History
            BottomNavButton(
                item = BottomNavItem.History,
                isSelected = currentRoute == BottomNavItem.History.route,
                onClick = { onNavigate(BottomNavItem.History.route) }
            )

            // QR Button (center)
            QRNavButton(
                item = BottomNavItem.QR,
                onClick = { onPayClick() }
            )

            // Profile
            BottomNavButton(
                item = BottomNavItem.Profile,
                isSelected = currentRoute == BottomNavItem.Profile.route,
                onClick = { onNavigate(BottomNavItem.Profile.route) }
            )

            // Settings
            BottomNavButton(
                item = BottomNavItem.Settings,
                isSelected = currentRoute == BottomNavItem.Settings.route,
                onClick = { onNavigate(BottomNavItem.Settings.route) }
            )
        }
    }
}

@Composable
private fun BottomNavButton(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (isSelected) Color(0xFF6366F1) else Color(0xFF94A3B8),
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSelected) Color(0xFF6366F1) else Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

@Composable
private fun QRNavButton(
    item: BottomNavItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                color = Color(0xFF6366F1),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}


