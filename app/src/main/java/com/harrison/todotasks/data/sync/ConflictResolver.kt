package com.harrison.todotasks.data.sync

import com.harrison.todotasks.data.model.TodoTask
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges concurrent edits to a [TodoTask] made on this device and on the server.
 */
@Singleton
class ConflictResolver @Inject constructor() {

    /**
     * Performs a three-way merge of [local] and [remote] against their common ancestor, [base].
     *
     * `title`, `description`, `dueAt`, and `isComplete` are merged independently, so edits to
     * different fields both survive. A field changed by only one side takes that side's value.
     * A field changed to different values by both sides takes the value from the side with the
     * later `lastUpdatedAt`, because that is the only timestamp available. On a tie, remote wins
     * because the server is the final authority.
     *
     * The remaining fields are resolved as follows:
     * - `id`, `createdAt`, and `parentTaskId` come from [remote].
     * - `version` comes from [remote], so a pending update can rebase on it.
     * - `deletedAt` is set if either side has a tombstone, preferring [remote] if both do. Deletes
     *   win over edits.
     * - `lastUpdatedAt` is the later of the [local] and [remote] values.
     *
     * @param base The last version both sides agreed on.
     * @param local The version on this device.
     * @param remote The version on the server.
     * @return The merged task.
     */
    fun resolve(base: TodoTask, local: TodoTask, remote: TodoTask): TodoTask {
        val localIsNewer = local.lastUpdatedAt > remote.lastUpdatedAt

        return TodoTask(
            id = remote.id,
            title = mergeField(base.title, local.title, remote.title, localIsNewer),
            description = mergeField(
                base.description,
                local.description,
                remote.description,
                localIsNewer,
            ),
            createdAt = remote.createdAt,
            lastUpdatedAt = maxOf(local.lastUpdatedAt, remote.lastUpdatedAt),
            deletedAt = remote.deletedAt ?: local.deletedAt,
            dueAt = mergeField(base.dueAt, local.dueAt, remote.dueAt, localIsNewer),
            parentTaskId = remote.parentTaskId,
            isComplete = mergeField(
                base.isComplete,
                local.isComplete,
                remote.isComplete,
                localIsNewer,
            ),
            version = remote.version,
        )
    }

    /**
     * Merges a single field. Remote is the default choice, which also covers the cases where
     * neither side changed the field or both sides set the same value.
     */
    private fun <T> mergeField(base: T, local: T, remote: T, localIsNewer: Boolean): T =
        when {
            local == base -> remote
            remote == base || localIsNewer -> local
            else -> remote
        }
}
