package br.com.marcocardoso.mobisentinel.monitoring.network

sealed interface CellularValidationResult {
    data object Validated : CellularValidationResult
    data object Unvalidated : CellularValidationResult
    data object Unavailable : CellularValidationResult
    data class Failure(val cause: Throwable) : CellularValidationResult
}

fun interface CellularValidationProbe {
    suspend fun validate(): CellularValidationResult
}
