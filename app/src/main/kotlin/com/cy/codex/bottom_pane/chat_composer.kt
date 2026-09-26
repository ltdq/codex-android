package com.cy.codex.bottom_pane

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
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
import com.cy.codex.bottom_pane.mentions_v2.MentionSuggestion
import com.cy.codex.bottom_pane.chat_composer.HistorySearchBar
import com.cy.codex.bottom_pane.chat_composer.historySearchMatches
import com.cy.codex.bottom_pane.chat_composer.nextHistoryMatch
import com.cy.codex.Motion
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.chatwidget.SlashCommand
import com.cy.codex.codeSurface
import com.cy.codex.glassTint
import com.cy.codex.keymap.KeyAction
import com.cy.codex.keymap.KeyContext
import com.cy.codex.keymap.LocalChatKeyFocus
import com.cy.codex.keymap.LocalShortcutsHelp
import com.cy.codex.keymap.codexHardwareKeys
import com.cy.codex.parentPath
import com.cy.codex.raisedSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The bottom composer: a direct port of the prompt bar it replaced, plus interrupt, queued
 * messages and the slash popup. Hardware keys are read here, not at the chat root, because the
 * popups and the caret are this widget's own state.
 */
private val ButtonSize = 42.dp

private val IdleGlyphSize = 21.dp

private val ActiveGlyphSize = 20.dp

private val InlineCornerRadius = 29.dp
private val StackedCornerRadius = 26.dp

private val CornerAnimationMs = Motion.ContentEnterMs

private val TintAnimationMs = Motion.TintMs

private val PopupFadeInMs = Motion.EnterMs
private val PopupFadeOutMs = Motion.ExitMs
private val RowFadeInMs = Motion.EnterMs

private val ComposerPaddingHorizontal = 7.dp
private val ComposerPaddingVertical = 8.dp
private val FieldPaddingHorizontal = 7.dp

private val PopupBottomGap = 6.dp
private val StackedRowGap = 6.dp

private val QueuedPaddingHorizontal = 12.dp
private val QueuedPaddingBottom = 6.dp
private val QueuedFontSize = UiType.Meta
private val QueuedLineHeight = UiType.FootnoteLine

private val PromptFontSize = UiType.Composer
private val PromptLineHeight = UiType.ComposerLine

// A button plus the two pixels that keep its ring inside the composer.
private val InputRowMinHeight = ButtonSize + 2.dp

/** Glass blur and shadow elevation; saturation stays at 1f — the boost only amplified low-frequency colour. */
private val ComposerBlurRadius = 14.dp
private val ComposerElevation = 12.dp

private val InlineChromeWidth = 26.dp

/** Rows the slash popup shows before it scrolls; one page, like the TUI popup. */
private const val MaxPopupRows = 5

private val SuggestionRowPaddingHorizontal = 11.dp
private val SuggestionRowPaddingVertical = 8.dp
private val SuggestionRowGap = 4.dp

private val SuggestionCommandWidth = 104.dp
private val SuggestionCommandGap = 10.dp

private fun PromptTextStyle(color: Color) =
    TextStyle(
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
    mentionSuggestions: List<MentionSuggestion> = emptyList(),
    onMentionPicked: (String) -> Unit = {},
    onMentionQueryChange: (String?) -> Unit = {},
    skillCandidates: List<String> = emptyList(),
    onSkillPicked: (String) -> Unit = {},
    /** Called on every text edit, so the host can defer an approval dialog while the user types. */
    onActivity: () -> Unit = {},
    /** Submitted drafts, newest first; the reverse search behind Ctrl+R walks this list. */
    history: List<String> = emptyList(),
    /** Ctrl+O: the host copies the last agent message; null leaves the chord unbound. */
    onCopyLastResponse: (() -> Unit)? = null,
    /** Ctrl+G: the host launches `ACTION_EDIT` on a temp file; null leaves the chord unbound. */
    onOpenExternalEditor: (() -> Unit)? = null,
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
    // Where the chat root lives so Esc can leave the field; null in previews and tests.
    val chatKeyFocus = LocalChatKeyFocus.current
    val shortcutsHelp = LocalShortcutsHelp.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    var inlineWidthPx by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()
    // Caret-carrying mirror of [value]: a String cannot say where a newline lands.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (field.text != value) {
            // Host rewrote the draft (picked command, inserted path); follow it and put the caret at the end.
            field = TextFieldValue(value, TextRange(value.length))
        }
    }
    // Esc dismisses a popup until the draft changes; a live trigger must not reopen it.
    var popupDismissed by remember { mutableStateOf(false) }
    var popupIndex by remember { mutableIntStateOf(0) }
    // Search shows the matched entry in the field so Enter's result is visible; the original draft returns on cancel.
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchIndex by remember { mutableIntStateOf(-1) }
    var searchOriginal by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val searchMatches =
        remember(history, searchQuery, searchActive) {
            if (searchActive) historySearchMatches(history, searchQuery) else emptyList()
        }
    LaunchedEffect(searchActive) {
        // The bar owns typing while it is up; focus keeps the hardware keyboard out of the matched entry.
        if (searchActive) runCatching { searchFocusRequester.requestFocus() }
    }
    val stacked =
        remember(value, inlineWidthPx, measurer) {
            if (value.isEmpty()) {
                false
            } else if (value.contains('\n')) {
                true
            } else if (inlineWidthPx <= 0f) {
                false
            } else {
                val measured =
                    measurer.measure(
                        text = AnnotatedString(value),
                        style = PromptTextStyle(colors.onSurface),
                        maxLines = 1,
                        softWrap = false,
                    )
                measured.size.width > inlineWidthPx
            }
        }
    val showSuggestions =
        !searchActive && !popupDismissed && slashSuggestions.isNotEmpty() && value.startsWith("/")
    // Trailing `@token`; a whitespace ends it, mirroring bottom_pane/mentions_v2/filter.rs.
    val mentionQuery =
        remember(value) {
            val at = value.lastIndexOf('@')
            when {
                at < 0 -> null
                value.substring(at + 1).any { it.isWhitespace() } -> null
                else -> value.substring(at + 1)
            }
        }
    // The host already narrowed the list; re-filtering here would drop server-weighted matches.
    val mentionRows =
        remember(mentionQuery, mentionSuggestions) {
            if (mentionQuery == null) emptyList() else mentionSuggestions.take(MaxPopupRows)
        }
    val commandRows = remember(slashSuggestions) { slashSuggestions.take(MaxPopupRows) }
    // Trailing `$token` with the same trigger rule as `@`, so a plain dollar amount never opens the popup.
    val skillQuery =
        remember(value) {
            val dollar = value.lastIndexOf('$')
            when {
                dollar < 0 -> null
                value.substring(dollar + 1).any { it.isWhitespace() } -> null
                else -> value.substring(dollar + 1)
            }
        }
    val skillRows =
        remember(skillQuery, skillCandidates) {
            if (skillQuery == null) {
                emptyList()
            } else {
                filterPaths(skillQuery, skillCandidates).take(MaxPopupRows)
            }
        }
    val showMentions =
        !searchActive && !popupDismissed && !showSuggestions && mentionRows.isNotEmpty()
    val showSkills =
        !searchActive &&
            !popupDismissed &&
            !showSuggestions &&
            !showMentions &&
            skillRows.isNotEmpty()
    val popupCount =
        when {
            showSuggestions -> commandRows.size
            showMentions -> mentionRows.size
            showSkills -> skillRows.size
            else -> 0
        }
    val popupSelection = if (popupCount == 0) 0 else popupIndex.coerceIn(0, popupCount - 1)

    // Re-select the first match when the filtered list changes, as `command_popup.rs` does.
    LaunchedEffect(showSuggestions, commandRows, showMentions, mentionRows, showSkills, skillRows) {
        popupIndex = 0
    }
    // The host owns the session; tell it which `@` token is live, or `null` when gone.
    LaunchedEffect(mentionQuery, showSuggestions) {
        onMentionQueryChange(if (showSuggestions) null else mentionQuery)
    }
    DisposableEffect(Unit) {
        onDispose { onMentionQueryChange(null) }
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
        val at = value.lastIndexOf('@')
        if (at < 0) return
        applyDraft(value.substring(0, at) + "@" + path + " ")
        onMentionPicked(path)
    }

    fun pickSkill(name: String) {
        val dollar = value.lastIndexOf('$')
        if (dollar < 0) return
        applyDraft(value.substring(0, dollar) + "$" + name + " ")
        onSkillPicked(name)
    }

    fun pickSuggestion(index: Int) {
        if (showSuggestions) {
            commandRows.getOrNull(index)?.let(onSuggestionPicked)
        } else if (showMentions) {
            mentionRows.getOrNull(index)?.let { pickMention(it.insert) }
        } else if (showSkills) {
            skillRows.getOrNull(index)?.let(::pickSkill)
        }
    }

    fun selectSearchMatch(index: Int) {
        searchIndex = index
        if (index in history.indices) applyDraft(history[index])
    }

    fun beginHistorySearch() {
        if (!enabled) return
        searchActive = true
        searchOriginal = value
        searchQuery = ""
        selectSearchMatch(history.indices.firstOrNull() ?: -1)
    }

    fun updateSearchQuery(next: String) {
        searchQuery = next
        selectSearchMatch(historySearchMatches(history, next).firstOrNull() ?: -1)
    }

    fun moveHistorySearch(older: Boolean) {
        selectSearchMatch(nextHistoryMatch(searchMatches, searchIndex, older))
    }

    fun acceptHistorySearch() {
        if (searchIndex in history.indices) applyDraft(history[searchIndex])
        searchActive = false
        runCatching { focusRequester.requestFocus() }
    }

    fun cancelHistorySearch() {
        applyDraft(searchOriginal)
        searchActive = false
        runCatching { focusRequester.requestFocus() }
    }

    fun handle(action: KeyAction): Boolean =
        when (action) {
            KeyAction.Submit -> {
                if (searchActive) {
                    acceptHistorySearch()
                } else if (enabled && value.isNotBlank()) {
                    onActivity()
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

            // `?` toggles shortcuts and must never eat a typed character, so it only acts on an empty field.
            KeyAction.ShowShortcuts -> {
                val help = shortcutsHelp
                if (value.isEmpty() && help != null) {
                    help.toggle()
                    // The overlay is modal; leaving the caret would let the soft keyboard type behind it.
                    leaveField()
                    true
                } else {
                    false
                }
            }

            KeyAction.ClearFocus -> {
                if (searchActive) cancelHistorySearch() else leaveField()
                true
            }

            // Ctrl+R begins the search on the newest entry; Ctrl+S only moves while one is up.
            KeyAction.HistoryOlder -> {
                if (!enabled) {
                    false
                } else {
                    if (searchActive) moveHistorySearch(older = true) else beginHistorySearch()
                    true
                }
            }

            KeyAction.HistoryNewer -> {
                if (enabled && searchActive) {
                    moveHistorySearch(older = false)
                    true
                } else {
                    false
                }
            }

            KeyAction.CopyLastResponse -> {
                val copy = onCopyLastResponse
                if (enabled && copy != null) {
                    copy()
                    true
                } else {
                    false
                }
            }

            KeyAction.OpenExternalEditor -> {
                val editor = onOpenExternalEditor
                if (enabled && editor != null) {
                    editor()
                    true
                } else {
                    false
                }
            }

            else -> false
        }

    val tint = glassTint(alpha = 0.86f)
    val cornerRadius by
        animateDpAsState(
            targetValue = if (stacked) stackedCornerRadius else inlineCornerRadius,
            animationSpec = tween(durationMillis = CornerAnimationMs),
            label = "promptCorner",
        )
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    val leading: @Composable () -> Unit = {
        // The launcher is registered by the screen, not here: re-registering on every recomposition would lose the selection result.
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
                val background by
                    animateColorAsState(
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

            value.isBlank() || !enabled ->
                IconButton(
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
                val background by
                    animateColorAsState(
                        targetValue = if (focused) colors.primary else colors.primaryVariant,
                        animationSpec = tween(durationMillis = TintAnimationMs),
                        label = "sendColor",
                    )
                IconButton(
                    onClick = {
                        onActivity()
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
        modifier =
            modifier
                // While a popup is open the arrow keys move its cursor; otherwise they move the caret below.
                .codexHardwareKeys(
                    if (popupCount > 0 && !searchActive) KeyContext.Popup else KeyContext.Composer,
                    ::handle,
                )
                .shadow(elevation = ComposerElevation, shape = shape, clip = false)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = { blur(ComposerBlurRadius.toPx()) },
                            onDrawSurface = { drawRect(tint) },
                        )
                    } else {
                        Modifier
                    }
                )
                .squircleSurface(
                    color = if (backdrop == null) tint else Color.Transparent,
                    cornerRadius = cornerRadius,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { focusRequester.requestFocus() },
                )
                .onSizeChanged { coords ->
                    inlineWidthPx =
                        coords.width -
                            with(density) {
                                (buttonSize * 2 + InlineChromeWidth).toPx()
                            }
                }
                .padding(
                    horizontal = ComposerPaddingHorizontal,
                    vertical = ComposerPaddingVertical,
                )
    ) {
        if (queuedCount > 0) {
            Text(
                text = stringResource(R.string.composer_queued_count, queuedCount),
                modifier =
                    Modifier.fillMaxWidth()
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
                    onPick = { pickMention(it.insert) },
                )
            }
        }
        AnimatedVisibility(
            visible = showSkills,
            enter = fadeIn(tween(PopupFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Column(modifier = Modifier.padding(bottom = PopupBottomGap)) {
                SkillSuggestionList(
                    candidates = skillRows,
                    selectedIndex = popupSelection,
                    onPick = ::pickSkill,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = InputRowMinHeight),
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
                modifier = Modifier.weight(1f).padding(horizontal = FieldPaddingHorizontal),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && !searchActive) {
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
                    // While searching, the field is a preview; typing belongs to the search bar.
                    readOnly = searchActive,
                    value = field,
                    onValueChange = { next ->
                        // Typing reopens a popup that Esc dismissed, if the trigger is still there.
                        popupDismissed = false
                        field = next
                        onActivity()
                        onValueChange(next.text)
                    },
                    modifier =
                        Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
                            focused = it.isFocused
                        },
                    textStyle = PromptTextStyle(colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = maxInputLines,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                onActivity()
                                onSubmit()
                                leaveField()
                            }
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
                modifier = Modifier.fillMaxWidth().padding(top = StackedRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        if (searchActive) {
            val searchStatus =
                when {
                    history.isEmpty() -> stringResource(R.string.composer_history_search_empty)
                    searchMatches.isEmpty() ->
                        stringResource(R.string.composer_history_search_no_match)
                    else ->
                        stringResource(
                            R.string.composer_history_search_position,
                            searchMatches.indexOf(searchIndex) + 1,
                            searchMatches.size,
                        )
                }
            HistorySearchBar(
                query = searchQuery,
                status = searchStatus,
                onQueryChange = ::updateSearchQuery,
                onOlder = { moveHistorySearch(older = true) },
                onNewer = { moveHistorySearch(older = false) },
                onAccept = ::acceptHistorySearch,
                onCancel = ::cancelHistorySearch,
                focusRequester = searchFocusRequester,
                enabled = enabled,
            )
        }
    }
}

private val PopupCorner = RoundedCornerShape(UiConsts.RowCorner)

private val PopupRowCorner = 11.dp

private val PopupRowGap = 4.dp
private val PopupPadding = 6.dp

/** Raised card both suggestion lists draw into; the lists self-limit, so the shell just wraps them. */
@Composable
private fun PopupShell(
    modifier: Modifier = Modifier,
    rows: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier.fillMaxWidth().background(codeSurface(), PopupCorner).padding(PopupPadding),
        verticalArrangement = Arrangement.spacedBy(PopupRowGap),
        content = { rows() },
    )
}

@Composable
private fun CommandSuggestionList(
    commands: List<SlashCommand>,
    selectedIndex: Int,
    onPick: (SlashCommand) -> Unit,
) {
    PopupShell {
        commands.forEachIndexed { index, command ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = rowShape,
                color =
                    if (index == selectedIndex) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        raisedSurface()
                    },
                onClick = { onPick(command) },
            ) {
                Row(
                    modifier =
                        Modifier.padding(
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
            }
            if (index != commands.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

@Composable
private fun MentionSuggestionList(
    candidates: List<MentionSuggestion>,
    selectedIndex: Int,
    onPick: (MentionSuggestion) -> Unit,
) {
    PopupShell {
        candidates.forEachIndexed { index, candidate ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = rowShape,
                color =
                    if (index == selectedIndex) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        raisedSurface()
                    },
                onClick = { onPick(candidate) },
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = SuggestionRowPaddingHorizontal,
                            vertical = SuggestionRowPaddingVertical,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = candidate.label,
                        modifier = Modifier.weight(1f),
                        fontSize = UiType.Subtitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail =
                        candidate.detail ?: parentPath(candidate.insert).takeIf { it.isNotEmpty() }
                    if (detail != null) {
                        Spacer(Modifier.width(SuggestionCommandGap))
                        Text(
                            text = detail,
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
            }
            if (index != candidates.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

/** `$`-skill popup with bare names, so two names differing only by scope stay tellable apart. */
@Composable
private fun SkillSuggestionList(
    candidates: List<String>,
    selectedIndex: Int,
    onPick: (String) -> Unit,
) {
    PopupShell {
        candidates.forEachIndexed { index, name ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = rowShape,
                color =
                    if (index == selectedIndex) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        raisedSurface()
                    },
                onClick = { onPick(name) },
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = SuggestionRowPaddingHorizontal,
                            vertical = SuggestionRowPaddingVertical,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$" + name,
                        modifier = Modifier.weight(1f),
                        fontSize = UiType.Subtitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index != candidates.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

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

/** Narrow [candidates] by [query]; subsequence hits come first, then plain contains. */
private fun filterPaths(query: String, candidates: List<String>): List<String> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return candidates
    val subsequence = mutableListOf<String>()
    val contains = mutableListOf<String>()
    for (candidate in candidates) {
        val haystack = candidate.lowercase()
        when {
            isSubsequence(needle, haystack) -> subsequence += candidate
            haystack.contains(needle) -> contains += candidate
        }
    }
    return subsequence + contains
}
