package com.example.expirytracker.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.expirytracker.ExpiryApplication
import com.example.expirytracker.data.Product
import com.example.expirytracker.data.ProductRepository
import com.example.expirytracker.ocr.ExtractedFields
import com.example.expirytracker.ocr.HybridExtractor
import com.example.expirytracker.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExtractionResult(
    val fields: ExtractedFields,
    val source: String,
    val imagePath: String,
)

class ProductViewModel(
    application: Application,
    private val repo: ProductRepository,
) : AndroidViewModel(application) {

    val products: StateFlow<List<Product>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extracting = MutableStateFlow(false)
    val extracting: StateFlow<Boolean> = _extracting

    private val _lastExtraction = MutableStateFlow<ExtractionResult?>(null)
    val lastExtraction: StateFlow<ExtractionResult?> = _lastExtraction

    fun clearExtraction() { _lastExtraction.value = null }

    fun runExtraction(bitmap: Bitmap) {
        viewModelScope.launch {
            _extracting.value = true
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    ImageUtils.saveBitmapToInternal(getApplication(), bitmap)
                }
                val (fields, source) = HybridExtractor.extract(bitmap)
                _lastExtraction.value = ExtractionResult(fields, source, savedPath)
            } finally {
                _extracting.value = false
            }
        }
    }

    suspend fun load(id: Long): Product? = repo.get(id)

    fun save(product: Product, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (product.id == 0L) repo.add(product) else {
                repo.update(product); product.id
            }
            onDone(id)
        }
    }

    fun delete(product: Product) {
        viewModelScope.launch {
            repo.delete(product)
            // Best-effort image cleanup
            product.imagePath?.let { runCatching { File(it).delete() } }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExpiryApplication
                ProductViewModel(app, app.repository)
            }
        }
    }
}
