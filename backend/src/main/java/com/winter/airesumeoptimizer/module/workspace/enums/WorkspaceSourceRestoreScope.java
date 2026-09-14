package com.winter.airesumeoptimizer.module.workspace.enums;

/**
 * Server-computed boundary of one authorized SOURCE restore operation.
 *
 * <p>Clients may only express "restore this frozen SOURCE occurrence"; which node is restored,
 * where it is restored, and how large the boundary is are resolved exclusively by the server
 * from the frozen manifest and the authenticated frozen ownership graph.
 */
public enum WorkspaceSourceRestoreScope {
    /** No server-authorized restore unit exists for this SOURCE block. */
    NONE,
    /** Restore one frozen bullet back into its existing frozen parent entry. */
    BULLET,
    /** Restore one entirely missing frozen entry into its existing frozen parent section. */
    ENTRY,
    /** Restore one entirely missing frozen Project entry (all fields, bullets and provenance). */
    PROJECT_ENTRY,
    /** Restore one deleted frozen contact field. */
    CONTACT
}
