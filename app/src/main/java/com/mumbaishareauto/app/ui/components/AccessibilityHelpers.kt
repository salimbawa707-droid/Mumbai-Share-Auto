package com.mumbaishareauto.app.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Ensures interactive components adhere to minimum touch target guidelines (48.dp x 48.dp)
 * and provide rich accessibility semantics for TalkBack and screen readers.
 */
fun Modifier.accessibleTouchTarget(
    label: String,
    role: Role = Role.Button,
    isHeading: Boolean = false
): Modifier = this.then(
    Modifier
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .semantics {
            contentDescription = label
            this.role = role
            if (isHeading) {
                heading()
            }
        }
)
