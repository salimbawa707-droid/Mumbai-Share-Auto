package com.mumbaishareauto.app.auth

import com.mumbaishareauto.app.UserMode

/**
 * Operating role selected during Phone OTP authentication and persisted to Cloud Firestore.
 */
enum class AuthUserRole(val label: String) {
    PASSENGER("Passenger"),
    DRIVER("Driver");

    fun toUserMode(): UserMode = when (this) {
        PASSENGER -> UserMode.PASSENGER
        DRIVER -> UserMode.DRIVER
    }

    companion object {
        fun fromFirestoreValue(raw: String?): AuthUserRole? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

        fun fromUserMode(mode: UserMode): AuthUserRole = when (mode) {
            UserMode.PASSENGER -> PASSENGER
            UserMode.DRIVER -> DRIVER
        }
    }
}

sealed class AuthFieldValidation {
    data object Valid : AuthFieldValidation()
    data class Invalid(val message: String) : AuthFieldValidation()

    val isValid: Boolean get() = this is Valid
}

/**
 * Authenticated user profile stored in Cloud Firestore at `/users/{uid}`.
 *
 * Truthfulness Rule:
 * - Phone OTP authenticates the commuter/driver account only.
 * - Commercial RTO vehicle permit compliance (`BookingDomain.VehicleCapacityConfig.isLegallyVerified`)
 *   remains separate and unverified (`false`) until an official RTO verification integration exists.
 */
data class AuthenticatedUserProfile(
    val uid: String,
    val phoneNumber: String,
    val role: AuthUserRole,
    val driverAutoRegNumber: String? = null,
    val createdAtMs: Long? = null,
    val updatedAtMs: Long? = null
) {
    fun normalized(): AuthenticatedUserProfile = copy(
        uid = uid.trim(),
        phoneNumber = AuthDomainValidator.normalizeIndianE164(phoneNumber) ?: phoneNumber.trim(),
        driverAutoRegNumber = if (role == AuthUserRole.DRIVER) {
            driverAutoRegNumber?.let { AuthDomainValidator.normalizeAutoRegNumber(it) }
        } else {
            null
        }
    )

    fun validate(): AuthFieldValidation {
        val cleanUid = uid.trim()
        if (cleanUid.isEmpty() || cleanUid.length > 128 || !AuthDomainValidator.UID_REGEX.matches(cleanUid)) {
            return AuthFieldValidation.Invalid("Authenticated Firebase UID is invalid.")
        }
        val cleanPhone = AuthDomainValidator.normalizeIndianE164(phoneNumber)
            ?: return AuthFieldValidation.Invalid("Phone number must be a valid 10-digit +91 Indian mobile number.")
        if (!AuthDomainValidator.INDIAN_E164_REGEX.matches(cleanPhone)) {
            return AuthFieldValidation.Invalid("Phone number must follow +91XXXXXXXXXX format.")
        }
        return when (role) {
            AuthUserRole.PASSENGER -> {
                if (driverAutoRegNumber != null) {
                    AuthFieldValidation.Invalid("Passenger profile must not include a driver auto registration number.")
                } else {
                    AuthFieldValidation.Valid
                }
            }
            AuthUserRole.DRIVER -> {
                when (val regValidation = AuthDomainValidator.validateDriverAutoRegNumber(driverAutoRegNumber.orEmpty())) {
                    is AuthFieldValidation.Invalid -> regValidation
                    AuthFieldValidation.Valid -> AuthFieldValidation.Valid
                }
            }
        }
    }

    /**
     * Converts into plain Kotlin map matching `/MumbaiShareAuto/firebase-blueprint.json` schema.
     * Note: server-timestamp placeholders are injected by the Firestore repository layer.
     */
    fun toBaseFirestoreFields(): Map<String, Any> {
        val safe = normalized()
        val fields = linkedMapOf<String, Any>(
            FIELD_UID to safe.uid,
            FIELD_PHONE_NUMBER to safe.phoneNumber,
            FIELD_ROLE to safe.role.name
        )
        if (safe.role == AuthUserRole.DRIVER && !safe.driverAutoRegNumber.isNullOrBlank()) {
            fields[FIELD_DRIVER_AUTO_REG_NUMBER] = safe.driverAutoRegNumber
        }
        return fields
    }

    companion object {
        const val COLLECTION_USERS = "users"
        const val FIELD_UID = "uid"
        const val FIELD_PHONE_NUMBER = "phoneNumber"
        const val FIELD_ROLE = "role"
        const val FIELD_DRIVER_AUTO_REG_NUMBER = "driverAutoRegNumber"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"

        fun fromFirestoreFields(
            documentId: String,
            data: Map<String, Any?>,
            createdAtMs: Long? = null,
            updatedAtMs: Long? = null
        ): AuthenticatedUserProfile? {
            val uid = (data[FIELD_UID] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: documentId.trim()
            val phone = (data[FIELD_PHONE_NUMBER] as? String)?.trim() ?: return null
            val role = AuthUserRole.fromFirestoreValue(data[FIELD_ROLE] as? String) ?: return null
            val rawReg = (data[FIELD_DRIVER_AUTO_REG_NUMBER] as? String)?.trim()
            val reg = if (role == AuthUserRole.DRIVER) {
                rawReg?.let { AuthDomainValidator.normalizeAutoRegNumber(it) }
            } else {
                null
            }
            val profile = AuthenticatedUserProfile(
                uid = uid,
                phoneNumber = phone,
                role = role,
                driverAutoRegNumber = reg,
                createdAtMs = createdAtMs,
                updatedAtMs = updatedAtMs
            )
            return if (profile.validate().isValid) profile else null
        }
    }
}

/**
 * Pure JVM validation & formatting rules for Indian (+91) Mobile OTP and Driver auto registration plates.
 */
object AuthDomainValidator {
    val UID_REGEX = Regex("^[a-zA-Z0-9_-]{1,128}$")
    val INDIAN_TEN_DIGIT_REGEX = Regex("^[6-9][0-9]{9}$")
    val INDIAN_E164_REGEX = Regex("^\\+91[6-9][0-9]{9}$")
    val OTP_SIX_DIGIT_REGEX = Regex("^[0-9]{6}$")

    // Standard Indian RTO vehicle registration number format, e.g. MH02AB1234 / MH01XY9999
    val INDIAN_AUTO_REG_REGEX = Regex("^[A-Z]{2}[0-9]{2}[A-Z]{1,3}[0-9]{4}$")

    fun sanitizeTenDigitInput(rawInput: String): String =
        rawInput.filter { it.isDigit() }.take(10)

    fun sanitizeOtpInput(rawInput: String): String =
        rawInput.filter { it.isDigit() }.take(6)

    fun normalizeAutoRegNumber(rawInput: String): String =
        rawInput.uppercase().filter { it.isLetterOrDigit() }.take(11)

    fun formatAutoRegForDisplay(compactReg: String): String {
        val clean = normalizeAutoRegNumber(compactReg)
        if (!INDIAN_AUTO_REG_REGEX.matches(clean)) return clean
        val state = clean.substring(0, 2)
        val rto = clean.substring(2, 4)
        val number = clean.takeLast(4)
        val series = clean.substring(4, clean.length - 4)
        return "$state $rto $series $number"
    }

    fun normalizeIndianE164(rawInput: String): String? {
        val trimmed = rawInput.trim().replace(" ", "").replace("-", "")
        val tenDigits = when {
            trimmed.startsWith("+91") -> trimmed.removePrefix("+91")
            trimmed.startsWith("91") && trimmed.length == 12 -> trimmed.removePrefix("91")
            trimmed.startsWith("0") && trimmed.length == 11 -> trimmed.removePrefix("0")
            else -> trimmed
        }
        return if (INDIAN_TEN_DIGIT_REGEX.matches(tenDigits)) "+91$tenDigits" else null
    }

    fun validateIndianMobileDigits(tenDigitInput: String): AuthFieldValidation {
        val cleaned = tenDigitInput.trim().replace(" ", "").replace("-", "")
        if (cleaned.isEmpty()) {
            return AuthFieldValidation.Invalid("Enter your 10-digit Indian mobile number.")
        }
        if (!cleaned.all { it.isDigit() }) {
            return AuthFieldValidation.Invalid("Mobile number must contain digits only.")
        }
        if (cleaned.length != 10) {
            return AuthFieldValidation.Invalid("Enter a complete 10-digit mobile number.")
        }
        if (!INDIAN_TEN_DIGIT_REGEX.matches(cleaned)) {
            return AuthFieldValidation.Invalid("Indian mobile numbers must start with 6, 7, 8, or 9.")
        }
        return AuthFieldValidation.Valid
    }

    fun validateOtpCode(otpInput: String): AuthFieldValidation {
        val cleaned = otpInput.trim()
        if (cleaned.isEmpty()) {
            return AuthFieldValidation.Invalid("Enter the 6-digit SMS OTP sent to your mobile number.")
        }
        if (!OTP_SIX_DIGIT_REGEX.matches(cleaned)) {
            return AuthFieldValidation.Invalid("OTP must be a 6-digit number.")
        }
        return AuthFieldValidation.Valid
    }

    fun validateDriverAutoRegNumber(rawInput: String): AuthFieldValidation {
        val compact = rawInput.trim().replace(" ", "").replace("-", "").uppercase()
        if (compact.isEmpty()) {
            return AuthFieldValidation.Invalid("Driver auto registration number is required (e.g. MH 02 AB 1234).")
        }
        if (compact.length < 9 || compact.length > 11 || !INDIAN_AUTO_REG_REGEX.matches(compact)) {
            return AuthFieldValidation.Invalid("Enter a valid Indian RTO plate (e.g. MH 02 AB 1234).")
        }
        return AuthFieldValidation.Valid
    }

    fun validateSendOtpRequest(
        role: AuthUserRole,
        tenDigitMobile: String,
        driverAutoRegInput: String
    ): AuthFieldValidation {
        val phoneResult = validateIndianMobileDigits(tenDigitMobile)
        if (phoneResult is AuthFieldValidation.Invalid) return phoneResult
        if (role == AuthUserRole.DRIVER) {
            val regResult = validateDriverAutoRegNumber(driverAutoRegInput)
            if (regResult is AuthFieldValidation.Invalid) return regResult
        }
        return AuthFieldValidation.Valid
    }
}

enum class FirestoreOperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

data class FirestoreErrorInfo(
    val error: String,
    val operationType: FirestoreOperationType,
    val path: String,
    val uid: String?,
    val phoneNumber: String?
) {
    fun toDiagnosticJson(): String {
        fun esc(v: String?): String =
            v?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
        return buildString {
            append("{")
            append("\"error\":").append(esc(error)).append(",")
            append("\"operationType\":").append(esc(operationType.value)).append(",")
            append("\"path\":").append(esc(path)).append(",")
            append("\"authInfo\":{")
            append("\"userId\":").append(esc(uid)).append(",")
            append("\"phoneNumber\":").append(esc(phoneNumber))
            append("}")
            append("}")
        }
    }
}
