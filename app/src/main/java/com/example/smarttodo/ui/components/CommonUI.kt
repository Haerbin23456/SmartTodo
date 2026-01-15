package com.example.smarttodo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object SmartTodoCardDefaults {
    val OuterRadius = 20.dp
    val InnerRadius = 12.dp
    val CardShape = RoundedCornerShape(OuterRadius)
    val InnerShape = RoundedCornerShape(InnerRadius)
    
    @Composable
    fun cardBorder(
        isError: Boolean = false
    ) = BorderStroke(
        width = 0.5.dp,
        color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    @Composable
    fun outlinedCardColors() = CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun SmartOutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = SmartTodoCardDefaults.CardShape,
        border = SmartTodoCardDefaults.cardBorder(isError),
        colors = SmartTodoCardDefaults.outlinedCardColors(),
        content = content
    )
}
