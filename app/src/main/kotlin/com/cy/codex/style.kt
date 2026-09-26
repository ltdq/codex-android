package com.cy.codex

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@Composable
fun sheetSideMargin(): Dp {
    val width = LocalWindowInfo.current.containerDpSize.width
    return (width * UiConsts.SheetSideMarginFraction).coerceIn(
        UiConsts.SheetSideMarginMin,
        UiConsts.SheetSideMarginMax,
    )
}
