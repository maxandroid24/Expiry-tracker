package com.example.expirytracker.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.expirytracker.data.Product
import com.example.expirytracker.utils.DateUtils
import com.example.expirytracker.utils.ImageUtils
import com.example.expirytracker.viewmodel.ProductViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    vm: ProductViewModel,
    productId: Long?,
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var mfgDate by remember { mutableStateOf<Long?>(null) }
    var expDate by remember { mutableStateOf<Long?>(null) }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var loadedId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productId) {
        if (productId != null && loadedId == null) {
            vm.load(productId)?.let { p ->
                name = p.name
                type = p.type
                mfgDate = p.manufacturingDate
                expDate = p.expiryDate
                imagePath = p.imagePath
                loadedId = p.id
            }
        }
    }

    // Apply extracted fields from camera/gallery if present.
    val extraction by vm.lastExtraction.collectAsState()
    LaunchedEffect(extraction) {
        extraction?.let { res ->
            res.fields.name?.let { if (name.isBlank()) name = it }
            res.fields.manufacturingDate?.let { if (mfgDate == null) mfgDate = it }
            res.fields.expiryDate?.let { if (expDate == null) expDate = it }
            if (imagePath.isNullOrBlank()) imagePath = res.imagePath
            vm.clearExtraction()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // Save then run extraction on the saved bitmap
        kotlinx.coroutines.MainScope().launch {
            val bmp = withContext(Dispatchers.IO) { ImageUtils.decodeUriToBitmap(context, uri) }
            if (bmp != null) {
                vm.runExtraction(bmp)
            }
        }
    }

    val productTypes = remember {
        listOf("Food", "Beverage", "Medicine", "Cosmetic", "Household", "Other")
    }
    val extracting by vm.extracting.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Add product" else "Edit product") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (productId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Image preview / actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!imagePath.isNullOrBlank() && File(imagePath!!).exists()) {
                    AsyncImage(
                        model = File(imagePath!!),
                        contentDescription = "Product image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        "No image yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (extracting) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Extracting…", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Camera")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Gallery")
                }
            }

            extraction?.let {
                Text(
                    "Auto-filled from ${it.source} (${(it.fields.confidence * 100).toInt()}% confidence)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    productTypes.take(3).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) }
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    productTypes.drop(3).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) }
                        )
                    }
                }
            }

            DateField(
                label = "Manufacturing date",
                value = mfgDate,
                onPick = { mfgDate = it }
            )
            DateField(
                label = "Expiry date",
                value = expDate,
                onPick = { expDate = it }
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    val mfg = mfgDate
                    val exp = expDate
                    when {
                        name.isBlank() -> error = "Please enter a product name."
                        exp == null -> error = "Please pick an expiry date."
                        mfg != null && exp <= mfg -> error = "Expiry must be after manufacturing date."
                        else -> {
                            error = null
                            val product = Product(
                                id = loadedId ?: 0L,
                                name = name.trim(),
                                type = type.ifBlank { "Other" },
                                manufacturingDate = mfg ?: DateUtils.todayUtcMillis(),
                                expiryDate = exp,
                                imagePath = imagePath
                            )
                            vm.save(product) { onBack() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) { Text(if (productId == null) "Save product" else "Update product") }

            Spacer(Modifier.size(20.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete product?") },
            text = { Text("This product will be removed from your list.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val current = Product(
                        id = loadedId ?: 0L,
                        name = name,
                        type = type,
                        manufacturingDate = mfgDate ?: DateUtils.todayUtcMillis(),
                        expiryDate = expDate ?: DateUtils.todayUtcMillis(),
                        imagePath = imagePath
                    )
                    vm.delete(current); onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DateField(label: String, value: Long?, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    val display = value?.let { DateUtils.format(it) } ?: "Pick a date"

    OutlinedButton(
        onClick = {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            value?.let { cal.timeInMillis = it }
            DatePickerDialog(
                context,
                { _, year, month, day -> onPick(DateUtils.toUtcMidnight(year, month, day)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(display, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
