package com.monicahq.android.vault

import androidx.annotation.StringRes
import com.monicahq.android.R

/** One compartment of the account, as the vault list and the vault screen show it. */
data class Vault(
    /** The UUID: the only stable reference (the slug changes when the vault is renamed). */
    val id: String,
    val name: String,
    /** Null when nobody described the vault. */
    val description: String?,
    val colour: VaultColour,
    /** Where records land when nothing says which vault. Exactly one vault is, while there is one. */
    val isDefault: Boolean,
    /** Archived and partial contacts included, as the instance counts them. */
    val contactCount: Int,
)

/**
 * The eight colours a vault can have, as the instance stores them (`VaultColourEnum`). They tell
 * vaults apart at a glance and carry no meaning. How each is drawn is in `VaultColours`.
 */
enum class VaultColour(
    val apiValue: String,
    @param:StringRes val label: Int,
) {
    Blue("#3f5fd0", R.string.colour_blue),
    Green("#4f8a49", R.string.colour_green),
    Ochre("#9a6a24", R.string.colour_ochre),
    Rust("#9a3f2b", R.string.colour_rust),
    Violet("#6b4fa8", R.string.colour_violet),
    Teal("#2f7f86", R.string.colour_teal),
    Rose("#a8447a", R.string.colour_rose),
    Graphite("#4a4a52", R.string.colour_graphite),
    ;

    companion object {
        /** A colour this app does not know yet (a newer instance) is drawn in neutral graphite. */
        fun fromApi(value: String?): VaultColour = entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: Graphite
    }
}

/** What the creation form sends to the instance. */
data class NewVault(
    val name: String,
    val description: String? = null,
    val colour: VaultColour = VaultColour.Blue,
    /** Only offered when the account already has a vault: the first one is always the default. */
    val makeDefault: Boolean = false,
)
