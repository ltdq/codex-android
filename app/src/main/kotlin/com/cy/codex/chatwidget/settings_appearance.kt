package com.cy.codex.chatwidget

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cy.codex.CodexButtonRow
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexSegmentedRow
import com.cy.codex.CodexSwitchRow
import com.cy.codex.CodexValue
import com.cy.codex.CodexValueRow
import com.cy.codex.DestinationCatalog
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.theme.Appearance
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 外观: the mode and reduce-motion choices the app keeps client-side, over the desktop's colour,
 * font and motion rows.
 *
 * The desktop's 减少动态效果 has a third state, 系统, that the app's boolean flag cannot hold. The
 * rows the port cannot act on stay disabled, holding the desktop's values, so the gap is visible;
 * none of them writes the config, so [context] goes unused.
 */
@Composable
internal fun SettingsAppearancePage(context: SettingsPageContext) {
    val uiContext = LocalContext.current

    CodexSection(stringResource(R.string.settings_appearance_group_visual)) {
        CodexSegmentedRow(
            title = stringResource(R.string.settings_appearance_mode),
            options =
                listOf(
                    stringResource(R.string.settings_theme_system),
                    stringResource(R.string.settings_theme_light),
                    stringResource(R.string.settings_theme_dark),
                ),
            selected = AppearanceThemeModes.indexOf(Appearance.themeMode).coerceAtLeast(0),
            onSelect = { Appearance.setThemeMode(uiContext, AppearanceThemeModes[it]) },
        )
        CodexRowDivider()
        AppearancePickerRow(R.string.settings_appearance_theme, "ChatGPT")
        CodexRowDivider()
        AppearancePickerRow(
            R.string.settings_appearance_accent_color,
            stringResource(R.string.settings_appearance_default),
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.settings_appearance_background),
            value = "#FFFFFF",
            monospace = false,
            enabled = false,
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.settings_appearance_foreground),
            value = "#1A1C1F",
            monospace = false,
            enabled = false,
        )
        CodexRowDivider()
        AppearancePickerRow(
            R.string.settings_appearance_font,
            stringResource(R.string.settings_appearance_system),
        )
    }

    // The desktop's 重置为默认设置 has no counterpart: nothing on the page holds an app-side value.
    CodexSection(stringResource(R.string.settings_appearance_group_advanced)) {
        CodexValueRow(
            title = stringResource(R.string.settings_appearance_interface_font_size),
            summary = stringResource(R.string.settings_appearance_interface_font_size_summary),
            value = "14 px",
            monospace = false,
            enabled = false,
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.settings_appearance_code_font_size),
            summary = stringResource(R.string.settings_appearance_code_font_size_summary),
            value = "12 px",
            monospace = false,
            enabled = false,
        )
    }

    // The desktop groups these two under 视觉风格 as well; the card carries no title of its own.
    CodexSection {
        CodexSegmentedRow(
            title = stringResource(R.string.settings_appearance_reduce_motion),
            summary = stringResource(R.string.settings_appearance_reduce_motion_summary),
            options =
                listOf(
                    stringResource(R.string.settings_appearance_reduce_motion_on),
                    stringResource(R.string.settings_appearance_reduce_motion_off),
                ),
            selected = if (Appearance.reduceMotion) 0 else 1,
            onSelect = { Appearance.setReduceMotion(uiContext, it == 0) },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_appearance_split_light_dark),
            summary = stringResource(R.string.settings_appearance_split_light_dark_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
    }

    // The group's cards carry no titles of their own: 高级 is the only heading above them.
    CodexSection {
        AppearancePickerRow(
            R.string.settings_appearance_interface_font_style,
            stringResource(R.string.settings_appearance_regular),
        )
        CodexRowDivider()
        AppearancePickerRow(
            R.string.settings_appearance_content_font,
            stringResource(R.string.settings_appearance_same_as_interface_font),
            stringResource(R.string.settings_appearance_regular),
        )
        CodexRowDivider()
        AppearancePickerRow(
            R.string.settings_appearance_code_font,
            stringResource(R.string.settings_appearance_system),
            stringResource(R.string.settings_appearance_regular),
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_appearance_translucent_sidebar),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.settings_appearance_contrast),
            value = "45",
            monospace = false,
            enabled = false,
        )
    }

    CodexSection {
        FixedSegmentedRow(
            title = R.string.settings_appearance_diff_markers,
            summary = R.string.settings_appearance_diff_markers_summary,
            options =
                listOf(
                    R.string.settings_appearance_diff_markers_color,
                    R.string.settings_appearance_diff_markers_signs,
                ),
            selected = 0,
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_appearance_pointer_cursor),
            summary = stringResource(R.string.settings_appearance_pointer_cursor_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
    }

    // The startup tip the app shows on a fresh session; the desktop capture has no row for it.
    CodexSection {
        CodexSwitchRow(
            title = stringResource(R.string.settings_show_tooltips),
            summary = stringResource(R.string.settings_show_tooltips_summary),
            checked = Appearance.showTooltips,
            onCheckedChange = { Appearance.setShowTooltips(uiContext, it) },
        )
    }
}

/**
 * 语音: the desktop's voice page — voice chat unavailable for the account, dictation off — over one
 * link into the app's own voice session, which the desktop page has no row for.
 */
@Composable
internal fun SettingsVoicePage(context: SettingsPageContext) {
    CodexSection(stringResource(R.string.settings_voice_group_general)) {
        FixedChoiceRow(
            title = R.string.settings_voice_microphone,
            summary = R.string.settings_voice_microphone_summary,
            valueRes = R.string.settings_voice_microphone_default,
        )
        CodexRowDivider()
        CodexButtonRow(
            title = stringResource(R.string.settings_voice_language),
            actionLabel = stringResource(R.string.settings_voice_language_retry),
            enabled = false,
            onAction = {},
        )
    }

    CodexSection(stringResource(R.string.settings_voice_group_voice_chat)) {
        CodexRow(
            title = stringResource(R.string.settings_voice_unavailable),
            summary = stringResource(R.string.settings_voice_unavailable_summary),
            enabled = false,
        )
        CodexRowDivider()
        CodexNavRow(
            title = stringResource(R.string.sidebar_library_realtime),
            onClick = { context.onOpenEntry(DestinationCatalog.Id.Realtime) },
        )
    }

    CodexSection(stringResource(R.string.settings_voice_group_dictation)) {
        CodexValueRow(
            title = stringResource(R.string.settings_voice_dictation_shortcut),
            summary = stringResource(R.string.settings_voice_dictation_shortcut_summary),
            value = stringResource(R.string.settings_voice_dictation_shortcut_off),
            monospace = false,
            enabled = false,
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.settings_voice_recent_recordings),
            summary = stringResource(R.string.settings_voice_recent_recordings_summary),
            value = "",
            enabled = false,
        )
    }

    // The dictionary is the 听写 group's third card, so it is not a group of its own.
    CodexSection {
        CodexButtonRow(
            title = stringResource(R.string.settings_voice_dictionary),
            summary = stringResource(R.string.settings_voice_dictionary_summary),
            actionLabel = stringResource(R.string.settings_voice_dictionary_add),
            enabled = false,
            onAction = {},
        )
        CodexRowDivider()
        CodexValueRow(title = "Jane Doe", value = "", enabled = false)
    }
}

/** The three modes [Appearance] stores; miuix's other [ColorSchemeMode]s are not offered. */
private val AppearanceThemeModes =
    listOf(ColorSchemeMode.System, ColorSchemeMode.Light, ColorSchemeMode.Dark)

/**
 * A disabled picker holding the desktop's value. [FixedChoiceRow] is the same row with a summary,
 * which no appearance picker has; a font row carries its weight picker beside the family picker.
 */
@Composable
private fun AppearancePickerRow(title: Int, vararg values: String) {
    CodexRow(
        title = stringResource(title),
        enabled = false,
        endAction = {
            values.forEachIndexed { index, value ->
                if (index > 0) Spacer(Modifier.width(UiConsts.Space8))
                CodexValue(value, monospace = false)
                Spacer(Modifier.width(UiConsts.Space4))
                Icon(
                    imageVector = MiuixIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconChevron),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        },
    )
}
