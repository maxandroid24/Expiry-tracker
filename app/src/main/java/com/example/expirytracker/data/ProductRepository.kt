package com.example.expirytracker.data

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {
    fun observeAll(): Flow<List<Product>> = dao.observeAll()
    suspend fun get(id: Long): Product? = dao.getById(id)
    suspend fun add(product: Product): Long = dao.insert(product)
    suspend fun update(product: Product) = dao.update(product)
    suspend fun delete(product: Product) = dao.delete(product)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
