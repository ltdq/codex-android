package com.cy.codex.app

import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTrustTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `trusted directories include descendants but not sibling prefixes`() {
        val trusted = mapOf("/work/project/" to true)

        assertTrue(isProjectTrusted("/work/project", trusted))
        assertTrue(isProjectTrusted("/work/project/src", trusted))
        assertFalse(isProjectTrusted("/work/project-copy", trusted))
        assertFalse(isProjectTrusted("/work", trusted))
    }

    @Test
    fun `parent traversal cannot inherit trust from the original prefix`() {
        val trusted = mapOf("/work/project" to true)

        assertTrue(isProjectTrusted("/work/project/src/..", trusted))
        assertFalse(isProjectTrusted("/work/project/../elsewhere", trusted))
        assertFalse(isProjectTrusted("/work/project/..", trusted))
    }

    @Test
    fun `relative and invalid paths cannot grant trust`() {
        assertFalse(isProjectTrusted("/work/project", emptyMap()))
        assertFalse(isProjectTrusted("project", mapOf("project" to true)))
        assertFalse(isProjectTrusted("/work/project", mapOf("" to true, "work" to true, "/work/\u0000" to true)))
        assertFalse(isProjectTrusted("/work/\u0000", mapOf("/work" to true)))
    }

    @Test
    fun `backslashes are literal characters in Android directory names`() {
        assertFalse(isProjectTrusted("/work\\project", mapOf("/work" to true)))
        assertTrue(isProjectTrusted("/work\\project/src", mapOf("/work\\project" to true)))
    }

    @Test
    fun `trusting the filesystem root includes absolute directories`() {
        assertTrue(isProjectTrusted("/work/project", mapOf("/" to true)))
        assertFalse(isProjectTrusted("work/project", mapOf("/" to true)))
    }

    @Test
    fun `the closest explicit project decision overrides inherited trust`() {
        val trusted = mapOf(
            "/work" to true,
            "/work/vendor" to false,
            "/work/vendor/approved" to true,
        )

        assertFalse(isProjectTrusted("/work/vendor", trusted))
        assertFalse(isProjectTrusted("/work/vendor/src", trusted))
        assertTrue(isProjectTrusted("/work/vendor/approved/src", trusted))
        assertTrue(isProjectTrusted("/work/app", trusted))
    }

    @Test
    fun `conflicting spellings of the same directory deny trust`() {
        val trusted = mapOf("/work/project" to true, "/work/./project/" to false)

        assertFalse(isProjectTrusted("/work/project/src", trusted))
        assertFalse(isProjectTrusted("/work/project/src", trusted.entries.reversed().associate { it.toPair() }))
    }

    @Test
    fun `symbolic links cannot inherit trust from a directory they escape`() {
        val trusted = temporaryFolder.newFolder("trusted").toPath()
        val outside = temporaryFolder.newFolder("outside").toPath()
        val link = Files.createSymbolicLink(trusted.resolve("link"), outside)
        val decisions = mapOf(trusted.toString() to true)

        assertFalse(isProjectTrusted(link.toString(), decisions))
        assertFalse(isProjectTrusted(link.resolve("new").toString(), decisions))
    }

    @Test
    fun `symbolic aliases resolve both trusted roots and explicit denials`() {
        val trusted = temporaryFolder.newFolder("trusted").toPath()
        val alias = Files.createSymbolicLink(temporaryFolder.root.toPath().resolve("alias"), trusted)
        val decisions = mapOf(alias.toString() to true, trusted.resolve("vendor").toString() to false)

        assertTrue(isProjectTrusted(trusted.resolve("src").toString(), decisions))
        assertFalse(isProjectTrusted(alias.resolve("vendor").toString(), decisions))
    }
}
