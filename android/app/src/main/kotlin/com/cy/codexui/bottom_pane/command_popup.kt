package com.cy.codexui.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.chatwidget.SlashCommand
import com.cy.codexui.UiConsts
import com.cy.codexui.codeSurface
import com.cy.codexui.pressableRow
import com.cy.codexui.raisedSurface
import com.cy.codexui.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Slash-command popup shown above the composer.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/command_popup.rs`: the filter is the first token after the
 * leading `/`, the first row is pre-selected, and the list is capped to one page so the composer
 * never walks off screen.
 */

/** Corner radius of the popup card; it is a card of rows, so it takes the shared row corner. */
internal val PopupCorner = RoundedCornerShape(UiConsts.RowCorner)

/** Corner radius of one popup row, shared with the `@`-mention popup. */
internal val PopupRowCorner = 11.dp

/** Room one popup row keeps inside itself. */
private val PopupRowPaddingHorizontal = 11.dp
private val PopupRowPaddingVertical = 8.dp

/** Gap between two popup rows, and the padding inside the popup shell. */
private val PopupRowGap = 4.dp
private val PopupPadding = 6.dp

/** Width reserved for the command column, and the gap before its description. */
private val CommandColumnWidth = 104.dp
private val CommandGap = 10.dp

/** Type of the command and of its description. */
private val CommandFontSize = UiType.Subtitle
private val CommandLineHeight = UiType.RowTitleLine
private val DescriptionFontSize = UiType.RowDetail
private val DescriptionLineHeight = UiType.MetaLine

@Composable
fun CommandPopup(
    commands: List<SlashCommand>,
    onPick: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
    maxRows: Int = 6,
) {
    val matches = commands.take(maxRows.coerceAtLeast(1))
    if (matches.isEmpty()) return
    PopupShell(modifier = modifier) {
        matches.forEachIndexed { index, command ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = rowShape,
                        container = if (index == 0) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            raisedSurface()
                        },
                        onClick = { onPick(command) },
                    )
                    .padding(
                        horizontal = PopupRowPaddingHorizontal,
                        vertical = PopupRowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = command.command,
                    modifier = Modifier.width(CommandColumnWidth),
                    fontSize = CommandFontSize,
                    lineHeight = CommandLineHeight,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(CommandGap))
                Text(
                    text = command.description,
                    modifier = Modifier.weight(1f),
                    fontSize = DescriptionFontSize,
                    lineHeight = DescriptionLineHeight,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index != matches.lastIndex) Spacer(Modifier.height(PopupRowGap))
        }
    }
}

/**
 * The raised card both popups draw into.
 *
 * Each popup already narrows itself to `maxRows` items, so the shell only has to wrap them: a
 * clipped scroll region would cut a row in half and hide the fact that more matches exist.
 */
@Composable
internal fun PopupShell(
    modifier: Modifier = Modifier,
    rows: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(codeSurface(), PopupCorner)
            .padding(PopupPadding),
        verticalArrangement = Arrangement.spacedBy(PopupRowGap),
        content = { rows() },
    )
}
