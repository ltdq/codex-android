package com.cy.codexui.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codexui.AppEvent
import com.cy.codexui.CatalogState
import com.cy.codexui.R
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.codeSurface
import com.cy.codexui.label
import com.cy.codexui.protocol.protocol.v2.SkillEntry
import com.cy.codexui.protocol.protocol.v2.SkillScope
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/skills` output as a page.
 *
 * Mirrors `skills/list` and `bottom_pane/skills_toggle_view.rs`: the TUI toggles skills in a
 * multi-select picker over one flat list; the phone groups the same entries by scope, because the
 * scope is what decides whether a skill can be turned off at all — a system skill is not the
 * user's to disable, and a flat list hides that.
 *
 * The switch writes through `skills/config/write` and the list is re-read from the server rather
 * than flipped locally: a skill the server refuses to change would otherwise appear to toggle.
 */
@Composable
fun SkillsScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val skills = catalog.skills
    val enabled = skills.count { it.enabled }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.skills_screen_title),
            subtitle = stringResource(R.string.skills_screen_subtitle, skills.size, enabled),
            leading = { SkillsBackButton(onBack) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            if (skills.isEmpty()) {
                SectionCard(title = stringResource(R.string.skills_screen_title), icon = MiuixIcons.Layers) {
                    Text(
                        text = stringResource(R.string.skills_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                }
            } else {
                SkillScope.entries.forEach { scope ->
                    val group = skills.filter { it.scope == scope }
                    if (group.isNotEmpty()) {
                        SkillsScopeCard(scope = scope, group = group, onEvent = onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillsScopeCard(
    scope: SkillScope,
    group: List<SkillEntry>,
    onEvent: (AppEvent) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        title = scope.label(),
        icon = skillsScopeIcon(scope),
        trailing = group.size.toString(),
    ) {
        group.forEachIndexed { index, skill ->
            if (index > 0) SkillsDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skill.name,
                            fontSize = UiType.RowTitle,
                            lineHeight = UiType.RowTitleLine,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(UiConsts.Space2))
                        Text(
                            text = if (skill.enabled) {
                                stringResource(R.string.skills_screen_enabled)
                            } else {
                                stringResource(R.string.skills_screen_disabled)
                            },
                            fontSize = UiType.Chip,
                            lineHeight = UiType.ChipLine,
                            color = if (skill.enabled) colors.primary else colors.disabledOnSurface,
                        )
                    }
                    Spacer(Modifier.width(UiConsts.Space10))
                    Switch(
                        checked = skill.enabled,
                        onCheckedChange = { onEvent(AppEvent.SetSkillEnabled(skill.name, it)) },
                    )
                }
                if (skill.description.isNotEmpty()) {
                    Spacer(Modifier.height(UiConsts.Space2))
                    Text(
                        text = skill.description,
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                if (skill.path.isNotEmpty()) {
                    Spacer(Modifier.height(UiConsts.Space5))
                    Text(
                        text = skill.path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SkillsRowShape)
                            .background(codeSurface())
                            .padding(horizontal = UiConsts.Space7, vertical = UiConsts.Space4),
                        fontSize = UiType.Code,
                        lineHeight = UiType.CodeLine,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillsBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.skills_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SkillsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiConsts.Space1)
            .height(UiConsts.DividerThickness)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

private val SkillsRowShape = RoundedCornerShape(UiConsts.RowCorner)

private fun skillsScopeIcon(scope: SkillScope): ImageVector = when (scope) {
    SkillScope.User -> MiuixIcons.Community
    SkillScope.Project -> MiuixIcons.FolderFill
    SkillScope.System -> MiuixIcons.Layers
    SkillScope.Admin -> MiuixIcons.Lock
}
