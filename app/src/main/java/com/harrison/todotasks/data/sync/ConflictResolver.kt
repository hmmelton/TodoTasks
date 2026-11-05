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
     * `title`, `description`, `dueAt`, and `isComplete` are merged independently. A field changed
     * by one side takes that side's value. A field changed by both takes the value from the side
     * with the later `lastUpdatedAt`, and remote on a tie.
     *
     * `id`, `createdAt`, and `version` come from [remote]. `deletedAt` is set if either side has a
     * tombstone, preferring [remote], so deletes win over edits. `lastUpdatedAt` is the later of
     * the two.
     *
     * @param base The last version both sides agreed on.
     * @param local The version on this device.
     * @param remote The version on the server.
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
            isComplete = mergeField(
                base.isComplete,
                local.isComplete,
                remote.isComplete,
                localIsNewer,
            ),
            version = remote.version,
        )
    }

    /** Merges a single field, defaulting to [remote]. */
    private fun <T> mergeField(base: T, local: T, remote: T, localIsNewer: Boolean): T =
        when {
            local == base -> remote
            remote == base || localIsNewer -> local
            else -> remote
        }
}
