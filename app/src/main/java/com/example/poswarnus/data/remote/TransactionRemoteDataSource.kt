package com.example.poswarnus.data.remote

import com.example.poswarnus.data.sync.PendingSale

interface TransactionRemoteDataSource {
    /** Idempotent upload of one sale. Returns normally both for a first upload and for "already there". Throws on any failure. */
    suspend fun submit(sale: PendingSale)
}
