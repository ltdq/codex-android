package com.cy.codexui.bottom_pane

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.R
import com.cy.codexui.chatwidget.SlashCommand
import com.cy.codexui.Motion
import com.cy.codexui.SquircleShape
import com.cy.codexui.fileName
import com.cy.codexui.floatingSurface
import com.cy.codexui.glassTint
import com.cy.codexui.keymap.KeyAction
import com.cy.codexui.keymap.KeyContext
import com.cy.codexui.keymap.LocalChatKeyFocus
import com.cy.codexui.keymap.LocalShortcutsHelp
import com.cy.codexui.keymap.codexHardwareKeys
import com.cy.codexui.parentPath
import com.cy.codexui.pressableRow
import com.cy.codexui.raisedSurface
import com.cy.codexui.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The bottom composer.
 *
 * A direct port of the prompt bar this file replaced — same stacked layout, same spring corner, same
 * glass surface — plus the three things the port of `bottom_pane/chat_composer.rs` needs on top:
 * a running turn turns the trailing button into an interrupt, queued messages are announced above
 * the field, and a leading `/` opens the slash-command popup.
 *
 * Hardware keys are read here rather than at the chat root because the popups and the caret are this
 * widget's own state: the host only hears about [KeyAction.Submit], [KeyAction.InterruptTurn] and the
 * global chords, while Enter/Shift+Enter and the popup cursor are resolved against the live draft.
 */

private val ButtonSize = 42.dp

/** Glyph of the leading button and of the idle send button. */
private val IdleGlyphSize = 21.dp

/** Glyph of the interrupt button and of the send button over a non-empty field. */
private val ActiveGlyphSize = 20.dp

/** Corner of the composer while the text sits inline, and once it has wrapped to a second row. */
private val InlineCornerRadius = 29.dp
private val StackedCornerRadius = 26.dp

/** How long the composer takes to change corner as the text stacks or unstack. */
private val CornerAnimationMs = Motion.ContentEnterMs

/** How long the trailing button takes to change colour as the turn starts or the field fills. */
private val TintAnimationMs = Motion.TintMs

/** Fade of a popup sliding in above the field, and of the rows that appear beside it. */
private val PopupFadeInMs = Motion.EnterMs
private val PopupFadeOutMs = Motion.ExitMs
private val RowFadeInMs = Motion.EnterMs

/** Vertical room the composer keeps around its button row, and inside its text column. */
private val ComposerPaddingHorizontal = 7.dp
private val ComposerPaddingVertical = 8.dp
private val FieldPaddingHorizontal = 7.dp

/** Gap between the button row and a popup above it, and above the stacked button row. */
private val PopupBottomGap = 6.dp
private val StackedRowGap = 6.dp

/** Room the queued-message line keeps inside the composer. */
private val QueuedPaddingHorizontal = 12.dp
private val QueuedPaddingBottom = 6.dp
private val QueuedFontSize = UiType.Meta
private val QueuedLineHeight = UiType.FootnoteLine

/** Type of the prompt and of its hint. */
private val PromptFontSize = UiType.Composer
private val PromptLineHeight = UiType.ComposerLine

/** Height of the input row: a button plus the two pixels that keep its ring inside the composer. */
private val InputRowMinHeight = ButtonSize + 2.dp

/**
 * Glass of the composer: its blur, and the elevation its shadow is cast from.
 *
 * Paired with `saturation = 1f` at the call site: the boost did nothing the tint was not already
 * doing, and it amplified whatever low-frequency colour the blur left behind.
 */
private val ComposerBlurRadius = 14.dp
private val NoBlurRadius = 0.dp
private val ComposerElevation = 12.dp

/** Room the two buttons and their paddings take out of the inline width of the field. */
private val InlineChromeWidth = 26.dp

/** Rows the slash popup shows before it scrolls; one page, like the TUI popup. */
private const val MaxPopupRows = 5

/** Room one suggestion row keeps inside itself, matching the tap-only popup cards. */
private val SuggestionRowPaddingHorizontal = 11.dp
private val SuggestionRowPaddingVertical = 8.dp
private val SuggestionRowGap = 4.dp

/** Width reserved for the command column, and the gap before its description. */
private val SuggestionCommandWidth = 104.dp
private val SuggestionCommandGap = 10.dp

private fun PromptTextStyle(color: Color) = TextStyle(
    color = color,
    fontSize = PromptFontSize,
    lineHeight = PromptLineHeight,
)

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onInterrupt: () -> Unit,
    running: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    hint: String = stringResource(R.string.composer_hint_idle),
    queuedCount: Int = 0,
    slashSuggestions: List<SlashCommand> = emptyList(),
    onSuggestionPicked: (SlashCommand) -> Unit = {},
    mentionCandidates: List<String> = emptyList(),
    onMentionPicked: (String) -> Unit = {},
    backdrop: Backdrop? = null,
    maxInputLines: Int = 6,
    /** Opens the system picker; the host registers the launcher, a composable cannot. */
    onAttach: () -> Unit = {},
    inlineCornerRadius: Dp = InlineCornerRadius,
    stackedCornerRadius: Dp = StackedCornerRadius,
    buttonSize: Dp = ButtonSize,
) {
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    // Where the chat root lives, so Esc can leave this field without leaving the window with no
    // key target at all. Null when the composer is hosted without a chat root (previews, tests).
    val chatKeyFocus = LocalChatKeyFocus.current
    val shortcutsHelp = LocalShortcutsHelp.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    var inlineWidthPx by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()
    // The caret-carrying mirror of [value]. The host owns the draft as plain text, but a newline has
    // to land where the caret is and a String cannot say where that is.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (field.text != value) {
            // The host rewrote the draft (a picked command, an inserted attachment path): follow it
            // and put the caret at the end, which is where typing would have left it.
            field = TextFieldValue(value, TextRange(value.length))
        }
    }
    // Esc hides a popup until the draft changes again; otherwise every keystroke that keeps the
    // trigger alive (a longer `/query`) would reopen what the user just closed.
    var popupDismissed by remember { mutableStateOf(false) }
    var popupIndex by remember { mutableIntStateOf(0) }
    val stacked = remember(value, inlineWidthPx, measurer) {
        if (value.isEmpty()) {
            false
        } else if (value.contains('\n')) {
            true
        } else if (inlineWidthPx <= 0f) {
            false
        } else {
            val measured = measurer.measure(
                text = AnnotatedString(value),
                style = PromptTextStyle(colors.onSurface),
                maxLines = 1,
                softWrap = false,
            )
            measured.size.width > inlineWidthPx
        }
    }
    val showSuggestions = !popupDismissed && slashSuggestions.isNotEmpty() && value.startsWith("/")
    // A mention is the trailing `@token`: everything after the last `@` counts as the query, and a
    // whitespace ends it. Mirrors the trigger rule in `bottom_pane/mentions_v2/filter.rs`.
    val mentionQuery = remember(value) {
        val at = value.lastIndexOf('@')
        when {
            at < 0 -> null
            value.substring(at + 1).any { it.isWhitespace() } -> null
            else -> value.substring(at + 1)
        }
    }
    val mentionMatches = remember(mentionQuery, mentionCandidates) {
        if (mentionQuery == null) {
            emptyList()
        } else {
            mentionCandidates.filter { path ->
                path.contains(mentionQuery, ignoreCase = true) ||
                    isSubsequence(mentionQuery, path)
            }.take(MaxPopupRows)
        }
    }
    val commandRows = remember(slashSuggestions) { slashSuggestions.take(MaxPopupRows) }
    // Keep the popup's own narrowing, so the row the keyboard cursor points at is the row the tap
    // targets would show.
    val mentionRows = remember(mentionQuery, mentionMatches) {
        filterPaths(mentionQuery.orEmpty(), mentionMatches).take(MaxPopupRows)
    }
    val showMentions = !popupDismissed && !showSuggestions && mentionRows.isNotEmpty()
    val popupCount = when {
        showSuggestions -> commandRows.size
        showMentions -> mentionRows.size
        else -> 0
    }
    val popupSelection = if (popupCount == 0) 0 else popupIndex.coerceIn(0, popupCount - 1)

    // `command_popup.rs` re-selects the first match when the filtered list changes, so a keystroke
    // never leaves the cursor on a row that no longer exists.
    LaunchedEffect(showSuggestions, commandRows, showMentions, mentionRows) {
        popupIndex = 0
    }

    fun leaveField() {
        val target = chatKeyFocus
        if (target == null || runCatching { target.requestFocus() }.isFailure) {
            focusManager.clearFocus()
        }
    }

    fun applyDraft(next: String) {
        field = TextFieldValue(next, TextRange(next.length))
        popupDismissed = false
        onValueChange(next)
    }

    fun insertNewline() {
        val selection = field.selection
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)
        val text = field.text
        val next = text.substring(0, start) + "\n" + text.substring(end)
        field = TextFieldValue(next, TextRange(start + 1))
        popupDismissed = false
        onValueChange(next)
    }

    fun pickMention(path: String) {
        // Replace the trailing token with the picked path, keeping what came before.
        val at = value.lastIndexOf('@')
        if (at < 0) return
        applyDraft(value.substring(0, at) + "@" + path + " ")
        onMentionPicked(path)
    }

    fun pickSuggestion(index: Int) {
        if (showSuggestions) {
            commandRows.getOrNull(index)?.let(onSuggestionPicked)
        } else if (showMentions) {
            mentionRows.getOrNull(index)?.let(::pickMention)
        }
    }

    fun handle(action: KeyAction): Boolean = when (action) {
        KeyAction.Submit -> {
            if (enabled && value.isNotBlank()) {
                onSubmit()
                leaveField()
            }
            true
        }

        KeyAction.InsertNewline -> {
            if (enabled) insertNewline()
            true
        }

        KeyAction.PopupNext -> {
            if (popupCount > 0) popupIndex = (popupSelection + 1) % popupCount
            popupCount > 0
        }

        KeyAction.PopupPrev -> {
            if (popupCount > 0) popupIndex = (popupSelection - 1 + popupCount) % popupCount
            popupCount > 0
        }

        KeyAction.PopupAccept -> {
            if (popupCount > 0) pickSuggestion(popupSelection)
            popupCount > 0
        }

        KeyAction.PopupDismiss -> {
            if (popupCount > 0) popupDismissed = true
            popupCount > 0
        }

        // `?` is the composer binding for `toggle_shortcuts`; it must never eat a printable
        // character the user is typing, so it only acts on an empty field.
        KeyAction.ShowShortcuts -> {
            val help = shortcutsHelp
            if (value.isEmpty() && help != null) {
                help.toggle()
                // The overlay is modal: leaving the caret here would let a soft keyboard keep
                // typing into the field behind it.
                leaveField()
                true
            } else {
                false
            }
        }

        KeyAction.ClearFocus -> {
            leaveField()
            true
        }

        else -> false
    }

    val tint = glassTint(alpha = 0.86f)
    val cornerRadius by animateDpAsState(
        targetValue = if (stacked) stackedCornerRadius else inlineCornerRadius,
        animationSpec = tween(durationMillis = CornerAnimationMs),
        label = "promptCorner",
    )
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }

    val leading: @Composable () -> Unit = {
        // The attachment button opens the system picker. `rememberLauncherForActivityResult` is
        // registered by the screen rather than here, because the contract it launches has to
        // outlive this composable: a picker that is re-registered on every recomposition loses the
        // result of a selection made while the sheet was open.
        IconButton(
            onClick = { if (enabled) onAttach() },
            minWidth = buttonSize,
            minHeight = buttonSize,
        ) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = stringResource(R.string.composer_add_attachment),
                modifier = Modifier.size(IdleGlyphSize),
                tint = colors.onSurfaceSecondary,
            )
        }
    }
    val trailing: @Composable () -> Unit = {
        when {
            running && value.isBlank() -> {
                val background by animateColorAsState(
                    targetValue = colors.error,
                    animationSpec = tween(durationMillis = TintAnimationMs),
                    label = "interruptColor",
                )
                IconButton(
                    onClick = onInterrupt,
                    backgroundColor = background,
                    cornerRadius = buttonSize / 2,
                    minWidth = buttonSize,
                    minHeight = buttonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Pause,
                        contentDescription = stringResource(R.string.composer_interrupt_turn),
                        modifier = Modifier.size(ActiveGlyphSize),
                        tint = colors.onError,
                    )
                }
            }

            value.isBlank() || !enabled -> IconButton(
                onClick = {},
                minWidth = buttonSize,
                minHeight = buttonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Send,
                    contentDescription = stringResource(R.string.composer_send),
                    modifier = Modifier.size(IdleGlyphSize),
                    tint = colors.onSurfaceSecondary,
                )
            }

            else -> {
                val background by animateColorAsState(
                    targetValue = if (focused) colors.primary else colors.primaryVariant,
                    animationSpec = tween(durationMillis = TintAnimationMs),
                    label = "sendColor",
                )
                IconButton(
                    onClick = {
                        onSubmit()
                        leaveField()
                    },
                    backgroundColor = background,
                    cornerRadius = buttonSize / 2,
                    minWidth = buttonSize,
                    minHeight = buttonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Send,
                        contentDescription = stringResource(R.string.composer_send),
                        modifier = Modifier.size(ActiveGlyphSize),
                        tint = colors.onPrimary,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            // Preview, not bubble: while a suggestion list is open the same arrow keys move its
            // cursor, and otherwise the field below keeps them for caret movement.
            .codexHardwareKeys(if (popupCount > 0) KeyContext.Popup else KeyContext.Composer, ::handle)
            .floatingSurface(
                shape = shape,
                tint = tint,
                backdrop = backdrop,
                blurRadius = if (backdrop != null) ComposerBlurRadius else NoBlurRadius,
                // No saturation boost: it amplifies exactly the low-frequency colour the blur left
                // behind, which is what the blotches were.
                saturation = 1f,
                elevation = ComposerElevation,
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { focusRequester.requestFocus() },
            )
            .onSizeChanged { coords ->
                inlineWidthPx = coords.width - with(density) {
                    (buttonSize * 2 + InlineChromeWidth).toPx()
                }
            }
            .padding(
                horizontal = ComposerPaddingHorizontal,
                vertical = ComposerPaddingVertical,
            ),
    ) {
        if (queuedCount > 0) {
            Text(
                text = stringResource(R.string.composer_queued_count, queuedCount),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = QueuedPaddingHorizontal,
                        end = QueuedPaddingHorizontal,
                        bottom = QueuedPaddingBottom,
                    ),
                fontSize = QueuedFontSize,
                lineHeight = QueuedLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        AnimatedVisibility(
            visible = showSuggestions,
            enter = fadeIn(tween(PopupFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Column(modifier = Modifier.padding(bottom = PopupBottomGap)) {
                CommandSuggestionList(
                    commands = commandRows,
                    selectedIndex = popupSelection,
                    onPick = onSuggestionPicked,
                )
            }
        }
        AnimatedVisibility(
            visible = showMentions,
            enter = fadeIn(tween(PopupFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Column(modifier = Modifier.padding(bottom = PopupBottomGap)) {
                MentionSuggestionList(
                    candidates = mentionRows,
                    selectedIndex = popupSelection,
                    onPick = ::pickMention,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = InputRowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = !stacked,
                enter = fadeIn(tween(RowFadeInMs)),
                exit = fadeOut(tween(PopupFadeOutMs)),
            ) {
                leading()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = FieldPaddingHorizontal),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        fontSize = PromptFontSize,
                        lineHeight = PromptLineHeight,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    enabled = enabled,
                    value = field,
                    onValueChange = { next ->
                        // Typing is what un-dismisses a popup: Esc closed it, the next character
                        // reopens it if the trigger is still there.
                        popupDismissed = false
                        field = next
                        onValueChange(next.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    textStyle = PromptTextStyle(colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = maxInputLines,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onSubmit()
                            leaveField()
                        },
                    ),
                    interactionSource = interactionSource,
                )
            }
            AnimatedVisibility(
                visible = !stacked,
                enter = fadeIn(tween(RowFadeInMs)),
                exit = fadeOut(tween(PopupFadeOutMs)),
            ) {
                trailing()
            }
        }
        AnimatedVisibility(
            visible = stacked,
            enter = fadeIn(tween(RowFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = StackedRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
    }
}

/**
 * The slash popup with a keyboard cursor.
 *
 * The shared `CommandPopup` highlights the first row because taps were its only input; a hardware
 * keyboard needs the highlight to move, so this draws the same rows with the cursor on whichever
 * row Enter would take. Row style is shared through [PopupShell], [PopupRowCorner] and
 * `pressableRow`, so a tap still picks exactly the row it lands on.
 */
@Composable
private fun CommandSuggestionList(
    commands: List<SlashCommand>,
    selectedIndex: Int,
    onPick: (SlashCommand) -> Unit,
) {
    PopupShell {
        commands.forEachIndexed { index, command ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = rowShape,
                        container = if (index == selectedIndex) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            raisedSurface()
                        },
                        onClick = { onPick(command) },
                    )
                    .padding(
                        horizontal = SuggestionRowPaddingHorizontal,
                        vertical = SuggestionRowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = command.command,
                    modifier = Modifier.width(SuggestionCommandWidth),
                    fontSize = UiType.Subtitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(SuggestionCommandGap))
                Text(
                    text = command.description,
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.RowDetail,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index != commands.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

/** The `@`-mention popup, with the same keyboard cursor as [CommandSuggestionList]. */
@Composable
private fun MentionSuggestionList(
    candidates: List<String>,
    selectedIndex: Int,
    onPick: (String) -> Unit,
) {
    PopupShell {
        candidates.forEachIndexed { index, path ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = rowShape,
                        container = if (index == selectedIndex) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            raisedSurface()
                        },
                        onClick = { onPick(path) },
                    )
                    .padding(
                        horizontal = SuggestionRowPaddingHorizontal,
                        vertical = SuggestionRowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileName(path),
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.Subtitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parent = parentPath(path)
                if (parent.isNotEmpty()) {
                    Spacer(Modifier.width(SuggestionCommandGap))
                    Text(
                        text = parent,
                        modifier = Modifier.weight(1f),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index != candidates.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

/**
 * Case-insensitive subsequence match, the same shape of filter the TUI's file search uses: `cpu`
 * matches `com/cy/codexui/ui/...`. Falls back to a plain `contains` in the caller, so a short query
 * still finds exact substrings.
 */
private fun isSubsequence(query: String, candidate: String): Boolean {
    if (query.isEmpty()) return true
    var index = 0
    for (char in candidate) {
        if (char.lowercaseChar() == query[index].lowercaseChar()) {
            index++
            if (index == query.length) return true
        }
    }
    return false
}
