package dev.sort.trino.validate

import dev.brikk.house.sql.intellij.pipe.NativeVerdict
import dev.brikk.house.sql.verify.TrinoVerifier
import dev.brikk.house.sql.verify.VerifyError
import dev.brikk.house.sql.verify.VerifyResult

internal object TrinoNativeValidation {
    private val verifier = TrinoVerifier()
    fun verify(sql: String): VerifyResult = verifier.verify(sql)
}

internal fun VerifyResult.toNativeVerdict(): NativeVerdict = when (this) {
    VerifyResult.Accepted -> NativeVerdict.Accepted
    is VerifyResult.Rejected -> NativeVerdict.Rejected(errors.joinToString("\n") { it.message })
    is VerifyResult.Unverified, is VerifyResult.Unavailable -> NativeVerdict.Unavailable
}

internal fun VerifyResult.nativeErrors(): List<VerifyError> = (this as? VerifyResult.Rejected)?.errors.orEmpty()
