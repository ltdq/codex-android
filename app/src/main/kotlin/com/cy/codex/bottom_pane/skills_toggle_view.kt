package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexEmptyRow
import com.cy.codex.CodexGroupTitle
import com.cy.codex.CodexPage
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.SkillEntry
import com.cy.codex.protocol.protocol.v2.SkillScope
import com.cy.codex.squircleShape
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/skills` as a page (skills/list, bottom_pane/skills_toggle_view.rs): grouped by scope, because
 * scope decides whether a skill can be turned off. Switches write through `skills/config/write`
 * and the list re-reads, so a server refusal does not appear to toggle.
 */
@Composable
fun SkillsScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val skills = catalog.skills
    val enabled = skills.count { it.enabled }

    CodexPage(
        title = stringResource(R.string.skills_screen_title),
        description = stringResource(R.string.skills_screen_subtitle, skills.size, enabled),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (skills.isEmpty()) {
            CodexEmptyRow(stringResource(R.string.skills_screen_empty))
        } else {
            SkillScope.entries.forEach { scope ->
                val group = skills.filter { it.scope == scope }
                if (group.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CodexGroupTitle(scope.label())
                        CodexCardGrid(count = group.size) { index ->
                            SkillCard(skill = group[index], onEvent = onEvent)
                        }
                    }
                }
            }
        }
    }
}

/** One skill as a catalogue card: its scope's mark, its name, what it does, and its switch. */
@Composable
private fun SkillCard(skill: SkillEntry, onEvent: (AppEvent) -> Unit) {
    CodexCatalogCard(
        title = skill.name,
        description = skill.description.ifEmpty { null },
        icon = skillsScopeIcon(skill.scope),
        enabled = skill.enabled,
        trailing = {
            Switch(
                checked = skill.enabled,
                onCheckedChange = { onEvent(AppEvent.SetSkillEnabled(skill.name, it)) },
            )
        },
        footer =
            if (skill.path.isEmpty()) {
                null
            } else {
                {
                    Text(
                        text = skill.path,
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(squircleShape(UiConsts.CornerChip))
                                .background(codeSurface())
                                .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
                        fontSize = UiType.Code,
                        lineHeight = UiType.CodeLine,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
    )
}

private fun skillsScopeIcon(scope: SkillScope): ImageVector =
    when (scope) {
        SkillScope.User -> MiuixIcons.Community
        SkillScope.Project -> MiuixIcons.FolderFill
        SkillScope.System -> MiuixIcons.Layers
        SkillScope.Admin -> MiuixIcons.Lock
    }
