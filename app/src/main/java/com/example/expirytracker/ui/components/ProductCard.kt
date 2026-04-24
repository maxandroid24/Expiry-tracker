package com.example.expirytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.expirytracker.data.Product
import com.example.expirytracker.ui.theme.Green40
import com.example.expirytracker.ui.theme.Green80
import com.example.expirytracker.ui.theme.Red40
import com.example.expirytracker.ui.theme.Red80
import com.example.expirytracker.utils.DateUtils
import java.io.File

@Composable
fun ProductCard(product: Product, onClick: () -> Unit) {
    val expired = DateUtils.isExpired(product.expiryDate)
    val accent = if (expired) Red40 else Green40
    val tint = if (expired) Red80 else Green80

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imagePath.isNullOrBlank() && File(product.imagePath).exists()) {
                    AsyncImage(
                        model = File(product.imagePath),
                        contentDescription = product.name,
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = product.name.firstOrNull()?.uppercase() ?: "?",
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (product.type.isNotBlank()) {
                    Text(
                        product.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (expired) "Expired on ${DateUtils.format(product.expiryDate)}"
                        else "Valid until ${DateUtils.format(product.expiryDate)}",
                        color = accent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
