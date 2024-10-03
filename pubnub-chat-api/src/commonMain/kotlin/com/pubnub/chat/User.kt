package com.pubnub.chat

import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import kotlin.js.JsName

/**
 * Represents an object that refers to a single user in a chat, including details about the user's identity, metadata, and actions they can perform.
 */
interface User {
    /**
     * Reference to the main [Chat] object.
     */
    val chat: Chat

    /**
     * Unique identifier for [User].
     */
    val id: String

    /**
     * 	Display name or username of the user (must not be empty or consist only of whitespace characters).
     */
    val name: String?

    /**
     * 	Identifier for the user from an external system, such as a third-party authentication provider or a user directory.
     */
    val externalId: String?

    /**
     * 	URL to the user's profile or avatar image.
     */
    val profileUrl: String?

    /**
     * 	User's email address.
     */
    val email: String?

    /**
     * Any custom properties or metadata associated with the user in the form of a map of key-value pairs.
     */
    val custom: Map<String, Any?>?

    /**
     * Current status of the user, like online, offline, or away.
     */
    val status: String?

    /**
     * Type of the user, like admin, member, guest.
     */
    val type: String?

    /**
     * Type of the user, like admin, member, guest.
     */
    val updated: String?

    /**
     * Timestamp for the last time the user information was updated or modified.
     */
    val lastActiveTimestamp: Long?

    /**
     * Indicates whether the user is currently (at the time of obtaining this `User` object) active.
     */
    val active: Boolean

    /**
     * Updates the metadata of the user with the provided details.
     *
     * @param name The new name for the user.
     * @param externalId The new external ID for the user.
     * @param profileUrl The new profile image URL for the user.
     * @param email The new email address for the user.
     * @param custom A map of custom properties or metadata for the user.
     * @param status The new status of the user (e.g., online, offline).
     * @param type The new type of the user (e.g., admin, member).
     *
     * @return [PNFuture] containing the updated [User].
     */
    fun update(
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
    ): PNFuture<User>

    /**
     * Deletes the user. If soft deletion is enabled, the user's data is retained but marked as inactive.
     *
     * @param soft If true, the user is soft deleted, retaining their data but making them inactive.
     * @return For hard delete, the method returns [PNFuture] with the last version of the [User] object before it was permanently deleted.
     * For soft delete, [PNFuture] containing an updated [User] instance with the status field set to "deleted".
     */
    fun delete(soft: Boolean = false): PNFuture<User>

    /**
     * Retrieves a list of channels where the user is currently present.
     *
     * @return [PNFuture] containing a list of channel IDs where the user is present.
     */
    fun wherePresent(): PNFuture<List<String>>

    /**
     * Checks whether the user is present in the specified channel.
     *
     * @param channelId The ID of the channel to check for the user's presence.
     * @return [PNFuture] containing a boolean indicating whether the user is present in the specified channel.
     */
    fun isPresentOn(channelId: String): PNFuture<Boolean>

    /**
     * Retrieves the memberships associated with the user across different channels.
     *
     * @param limit The maximum number of memberships to retrieve.
     * @param page Pagination information for retrieving memberships.
     * @param filter An expression to filter the retrieved memberships.
     * @param sort A collection of sort keys to determine the sort order of the memberships.
     *
     * @return [PNFuture] containing [MembershipsResponse] with the user's memberships.
     */
    fun getMemberships(
        limit: Int? = null,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<MembershipsResponse>

    /**
     * Sets/unset restrictions on the user within a specified channel, such as ban/unban or mut/unmute them.
     *
     * @param channel The [Channel] where the restrictions will be applied.
     * @param ban If true, the user is banned from the channel.
     * @param mute If true, the user is muted in the channel.
     * @param reason The reason for applying the restriction.
     *
     * @return [PNFuture] indicating the result of setting the restriction.
     */
    fun setRestrictions(
        channel: Channel,
        ban: Boolean = false,
        mute: Boolean = false,
        reason: String? = null,
    ): PNFuture<Unit>

    /**
     * Retrieves the restrictions applied to the user within a specified channel.
     *
     * @param channel The [Channel] for which to retrieve the restrictions.
     * @return [PNFuture] containing the [Restriction] applied to the user in the specified channel.
     */
    fun getChannelRestrictions(channel: Channel): PNFuture<Restriction>

    /**
     * Retrieves all restrictions applied to the user on all channels they are a member of.
     *
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc.
     *
     * @return [PNFuture] containing [GetRestrictionsResponse] with the user's restrictions across channels.
     */
    fun getChannelsRestrictions(
        limit: Int? = null,
        page: PNPage? = null,
        sort: Collection<PNSortKey<PNMembershipKey>> = listOf(),
    ): PNFuture<GetRestrictionsResponse>

    /**
     * Receives updates on a single User object.
     *
     * @param callback A Function that is triggered whenever the user's information are changed (added, edited, or removed)
     * @return AutoCloseable Interface that lets you stop receiving user-related updates (objects events)
     * and clean up resources by invoking the close() method.
     */
    fun streamUpdates(callback: (user: User?) -> Unit): AutoCloseable

    /**
     * Checks if the user is currently active (e.g., online).
     *
     * @return [PNFuture] containing a boolean indicating whether the user is active.
     */
    @Deprecated("Use non-async `active` property instead.", ReplaceWith("active"))
    @JsName("_active")
    fun active(): PNFuture<Boolean>

    /**
     * Get a new `User` instance that is a copy of this `User` with its properties updated with information coming from `update`.
     */
    operator fun plus(update: PNUUIDMetadata): User

    companion object
}
