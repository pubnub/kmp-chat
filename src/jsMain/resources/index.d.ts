/// <reference types="pubnub" />
import PubNub from "pubnub";
import { AppContext, Publish, FileSharing, Signal, Subscription, History, Payload } from "pubnub";
type MembershipFields = Pick<Membership, "channel" | "user" | "custom" | "updated" | "eTag" | "status" | "type">;
declare class Membership {
    private chat;
    readonly channel: Channel;
    readonly user: User;
    readonly custom?: AppContext.CustomData | null;
    readonly updated: string;
    readonly eTag: string;
    readonly status?: string;
    readonly type?: string;
    update(params?: {
        status?: string;
        type?: string;
        custom?: AppContext.CustomData;
    }): Promise<Membership>;
    delete(): Promise<boolean>;
    /*
    * Updates
    */
    static streamUpdatesOn(memberships: Membership[], callback: (memberships: Membership[]) => unknown): () => void;
    /** @deprecated Use onUpdated() and onDeleted() instead. */
    streamUpdates(callback: (membership: Membership | null) => unknown): () => void;
    onUpdated(callback: (membership: Membership) => void): () => void;
    onDeleted(callback: () => void): () => void;
    /*
    * Unread message counts
    */
    get lastReadMessageTimetoken(): string | number | boolean | undefined;
    setLastReadMessage(message: Message): Promise<Membership>;
    setLastReadMessageTimetoken(timetoken: string): Promise<Membership>;
    getUnreadMessagesCount(): Promise<number | false>;
}
type UserFields = Pick<User, "id" | "name" | "externalId" | "profileUrl" | "email" | "custom" | "status" | "type">;
declare class User {
    private chat;
    readonly id: string;
    readonly name?: string;
    readonly externalId?: string;
    readonly profileUrl?: string;
    readonly email?: string;
    readonly custom?: AppContext.CustomData;
    readonly status?: string;
    readonly type?: string;
    readonly updated?: string;
    readonly lastActiveTimestamp?: number;
    get active(): boolean;
    /*
    * CRUD
    */
    update(data: Omit<UserFields, "id">): Promise<User>;
    delete(): Promise<true>;
    /*
    * Updates
    */
    static streamUpdatesOn(users: User[], callback: (users: User[]) => unknown): () => void;
    /** @deprecated Use onUpdated() and onDeleted() instead. */
    streamUpdates(callback: (user: User | null) => unknown): () => void;
    onUpdated(callback: (user: User) => void): () => void;
    onDeleted(callback: () => void): () => void;
    onMentioned(callback: (mention: Mention) => void): () => void;
    onInvited(callback: (invite: Invite) => void): () => void;
    onRestrictionChanged(callback: (restriction: {
        userId: string;
        channelId: string;
        ban: boolean;
        mute: boolean;
        reason?: string;
    }) => void): () => void;
    /*
    * Presence
    */
    wherePresent(): Promise<string[]>;
    isPresentOn(channelId: string): Promise<boolean>;
    /*
    * Memberships
    */
    getMemberships(params?: Omit<AppContext.GetMembershipsParameters, "include" | "uuid">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        memberships: Membership[];
    }>;
    isMemberOf(channelId: string): Promise<boolean>;
    getMembership(channelId: string): Promise<Membership | null>;
    /**
     * Moderation restrictions
     */
    setRestrictions(channel: Channel, params: {
        ban?: boolean;
        mute?: boolean;
        reason?: string;
    }): Promise<void>;
    getChannelRestrictions(channel: Channel): Promise<{
        ban: boolean;
        mute: boolean;
        reason: string | number | boolean | undefined;
    }>;
    getChannelsRestrictions(params?: Pick<AppContext.GetMembersParameters, "limit" | "page" | "sort">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        restrictions: {
            ban: boolean;
            mute: boolean;
            reason: string | number | boolean | undefined;
            channelId: string;
        }[];
    }>;
    /*
    * Other
    */
    /** @deprecated */
    DEPRECATED_report(reason: string): Promise<Signal.SignalResponse>;
}
type EventFields<T extends EventType> = Pick<Event<T>, "timetoken" | "type" | "payload" | "channelId" | "userId">;
declare class Event<T extends EventType> {
    private chat;
    readonly timetoken: string;
    readonly type: T;
    readonly payload: EventPayloads[T];
    readonly channelId: string;
    readonly userId: string;
}
type ChannelType = "direct" | "group" | "public" | "unknown";
declare enum MessageType {
    TEXT = "text"
}
declare enum MessageActionType {
    REACTIONS = "reactions",
    DELETED = "deleted",
    EDITED = "edited"
}
declare class ConnectionStatusCategory {
    readonly value: string;
    toString(): string;
    equals(other: ConnectionStatusCategory): boolean;
    
    static readonly PN_CONNECTION_ONLINE: ConnectionStatusCategory;
    static readonly PN_CONNECTION_OFFLINE: ConnectionStatusCategory;
    static readonly PN_CONNECTION_ERROR: ConnectionStatusCategory;
}

type TextMessageContent = {
    type: MessageType.TEXT;
    text: string;
    files?: {
        name: string;
        id: string;
        url: string;
        type?: string;
    }[];
};
/** @deprecated Use channel.onTypingChanged() instead. */
type TypingEventParams = {
    type: "typing";
    channel: string;
};
/** @deprecated Use channel.onMessageReported() instead. */
type ReportEventParams = {
    type: "report";
    channel: string;
};
/** @deprecated Use membership.setLastReadMessage() / membership.setLastReadMessageTimetoken() and channel.fetchReadReceipts() instead. */
type ReceiptEventParams = {
    type: "receipt";
    channel: string;
};
/** @deprecated Use user.onMentioned() instead. */
type MentionEventParams = {
    type: "mention";
    user: string;
};
/** @deprecated Use user.onInvited() instead. */
type InviteEventParams = {
    type: "invite";
    channel: string;
};
/** @deprecated Use user.onRestrictionChanged() instead. */
type ModerationEventParams = {
    type: "moderation";
    channel: string;
};
/** @deprecated Use channel.emitCustomEvent() and channel.onCustomEvent() instead. */
type CustomEventParams = {
    type: "custom";
    method: "signal" | "publish";
    channel: string;
};
/** @deprecated Use the corresponding method on the entity (channel or user) instead, e.g. channel.onTypingChanged(), user.onMentioned(). */
type EventParams = {
    typing: TypingEventParams;
    report: ReportEventParams;
    receipt: ReceiptEventParams;
    mention: MentionEventParams;
    invite: InviteEventParams;
    custom: CustomEventParams;
    moderation: ModerationEventParams;
};
/** @deprecated Use channel.onTypingChanged() instead. */
type TypingEventPayload = {
    value: boolean;
};
/** @deprecated Use channel.onMessageReported() instead. */
type ReportEventPayload = {
    text?: string;
    reason: string;
    reportedMessageTimetoken?: string;
    reportedMessageChannelId?: string;
    reportedUserId?: string;
    autoModerationId?: string;
};
/** @deprecated Use membership.setLastReadMessage() / membership.setLastReadMessageTimetoken() and channel.fetchReadReceipts() instead. */
type ReceiptEventPayload = {
    messageTimetoken: string;
};
/** @deprecated Use Mention type with user.onMentioned() instead. */
type MentionEventPayload = {
    messageTimetoken: string;
    channel: string;
};
/** @deprecated Use Invite type with user.onInvited() instead. */
type InviteEventPayload = {
    channelType: ChannelType | "unknown";
    channelId: string;
};
/** @deprecated Use user.onRestrictionChanged() instead. */
type ModerationEventPayload = {
    channelId: string;
    restriction: "muted" | "banned" | "lifted";
    reason?: string;
};
type CustomEventPayload = any;
type CustomEventData = {
    timetoken: string;
    userId: string;
    payload: CustomEventPayload;
    type?: string;
};
type CustomEventEmitOptions = {
    messageType?: string;
    storeInHistory?: boolean;
};
type CustomEventListenOptions = {
    messageType?: string;
};
type MessageReport = {
    reason: string;
    text?: string;
    messageTimetoken?: string;
    reportedMessageChannelId?: string;
    reportedUserId?: string;
    autoModerationId?: string;
};
/** @deprecated Use the corresponding method on the entity (channel or user) instead, e.g. channel.onTypingChanged(), user.onMentioned(). */
type EventPayloads = {
    typing: TypingEventPayload;
    report: ReportEventPayload;
    receipt: ReceiptEventPayload;
    mention: MentionEventPayload;
    invite: InviteEventPayload;
    moderation: ModerationEventPayload;
    custom: CustomEventPayload;
};
/** @deprecated Use the corresponding method on the entity (channel or user) instead, e.g. channel.onTypingChanged(), user.onMentioned(). */
type EmitEventParams = (TypingEventParams & {
    payload: TypingEventPayload;
}) | (ReportEventParams & {
    payload: ReportEventPayload;
}) | (ReceiptEventParams & {
    payload: ReceiptEventPayload;
}) | (MentionEventParams & {
    payload: MentionEventPayload;
}) | (InviteEventParams & {
    payload: InviteEventPayload;
}) | (CustomEventParams & {
    payload: CustomEventPayload;
}) | (ModerationEventParams & {
    payload: ModerationEventPayload;
});
type EventType = "typing" | "report" | "receipt" | "mention" | "invite" | "custom" | "moderation";
type GenericEventParams<T extends keyof EventParams> = EventParams[T];
type MessageActions = {
    [type: string]: {
        [value: string]: Array<{
            uuid: string;
            actionTimetoken: string | number;
        }>;
    };
};
type MessageReaction = {
    value: string;
    isMine: boolean;
    userIds: string[];
};
type ReadReceipt = {
    userId: string;
    lastReadTimetoken: string;
};
type ReadReceiptsResponse = {
    page: {
        next?: string;
        prev?: string;
    };
    total: number;
    status: number;
    receipts: ReadReceipt[];
};
type DeleteParameters = {
    soft?: boolean;
};
type MessageMentionedUsers = {
    [nameOccurrenceIndex: number]: {
        id: string;
        name: string;
    };
};
type MessageReferencedChannels = {
    [nameOccurrenceIndex: number]: {
        id: string;
        name: string;
    };
};
type SendTextParams = {
    meta?: any;
    storeInHistory?: boolean;
    sendByPost?: boolean;
    ttl?: number;
    customPushData?: Record<string, string>;
};
type MessageDraftOptions = Omit<Publish.PublishParameters, "message" | "channel">;
type SendTextOptionParams = Omit<Publish.PublishParameters, "message" | "channel"> & {
    mentionedUsers?: MessageMentionedUsers;
    referencedChannels?: MessageReferencedChannels;
    textLinks?: TextLink[];
    quotedMessage?: Message;
    files?: FileList | File[] | FileSharing.SendFileParameters<PubNub.PubNubFileParameters>["file"][];
    customPushData?: Record<string, string>;
};
type EnhancedMessageEvent = Subscription.Message;
type MessageDTOParams = History.FetchMessagesForChannelsResponse['channels'][string][number] | History.FetchMessagesWithActionsResponse['channels'][string][number] | EnhancedMessageEvent;
type ThreadMessageDTOParams = MessageDTOParams & {
    parentChannelId: string;
};
type MembershipResponse = Awaited<ReturnType<User["getMemberships"]>>;
type OptionalAllBut<T, K extends keyof T> = Partial<T> & Pick<T, K>;
type ChannelDTOParams = OptionalAllBut<AppContext.ChannelMetadataObject<AppContext.CustomData>, "id"> & {
    status?: string | null;
    type?: ChannelType | null | string;
};
type ThreadChannelDTOParams = ChannelDTOParams & {
    parentChannelId: string;
    parentMessage: Message;
};
type MessageDraftConfig = {
    userSuggestionSource: "channel" | "global";
    isTypingIndicatorTriggered: boolean;
    userLimit: number;
    channelLimit: number;
};
type TextLink = {
    startIndex: number;
    endIndex: number;
    link: string;
};
type GetLinkedTextParams = {
    mentionedUserRenderer: (userId: string, mentionedName: string) => any;
    plainLinkRenderer: (link: string) => any;
    textLinkRenderer: (text: string, link: string) => any;
};
type PayloadForTextTypes = {
    text: {
        text: string;
    };
    mention: {
        name: string;
        id: string;
    };
    plainLink: {
        link: string;
    };
    textLink: {
        text: string;
        link: string;
    };
    channelReference: {
        name: string;
        id: string;
    };
};
type TextTypes = keyof PayloadForTextTypes;
type MentionType = "mention" | "channelReference" | "textLink";
type TextTypeElement<T extends TextTypes> = {
    type: T;
    content: PayloadForTextTypes[T];
};
type MixedTextTypedElement = TextTypeElement<"text"> | TextTypeElement<"mention"> | TextTypeElement<"plainLink"> | TextTypeElement<"textLink"> | TextTypeElement<"channelReference">;
type ErrorLoggerSetParams = {
    key: string;
    error: unknown;
    thrownFunctionArguments: IArguments;
};
declare class ErrorLoggerImplementation {
    setItem(key: string, params: ErrorLoggerSetParams): void;
    getStorageObject(): Record<string, unknown>;
}
/** @deprecated Use UserMention instead. */
type ChannelMentionData = {
    event: Event<"mention">;
    channelId: string;
    message: Message;
    userId: string;
};
/** @deprecated Use UserMention instead. */
type ThreadMentionData = {
    event: Event<"mention">;
    parentChannelId: string;
    threadChannelId: string;
    message: Message;
    userId: string;
};
/** @deprecated Use UserMention instead. */
type UserMentionData = ChannelMentionData | ThreadMentionData;
type Mention = {
    messageTimetoken: string;
    channelId: string;
    parentChannelId?: string;
    mentionedByUserId: string;
};
type Invite = {
    channelId: string;
    channelType: ChannelType;
    invitedByUserId: string;
    invitationTimetoken: string;
};
type UserMention = {
    message: Message;
    userId: string;
    channelId: string;
    parentChannelId?: string;
};
type MessageFields = Pick<Message, "timetoken" | "content" | "channelId" | "userId" | "actions" | "meta">;
type CreateThreadResult = {
    threadChannel: ThreadChannel;
    parentMessage: Message;
};
declare class Message {
    protected chat: Chat;
    readonly timetoken: string;
    readonly content: TextMessageContent;
    readonly channelId: string;
    readonly userId: string;
    /** @deprecated Use `Message.reactions` instead for accessing message reactions. */
    readonly actions?: MessageActions;
    readonly meta?: {
        [key: string]: any;
    };
    readonly error?: string;
    get hasThread(): boolean;
    get mentionedUsers(): any;
    get referencedChannels(): any;
    get textLinks(): any;
    get type(): MessageType;
    get quotedMessage(): QuotedMessage | null | undefined;
    get files(): {
        name: string;
        id: string;
        url: string;
        type?: string | undefined;
    }[];
    /*
    * Updates
    */
    static streamUpdatesOn(messages: Message[], callback: (messages: Message[]) => unknown): () => void;
    /** @deprecated Use onUpdated() instead. */
    streamUpdates(callback: (message: Message) => unknown): () => void;
    onUpdated(callback: (message: Message) => void): () => void;
    /*
    * Message text
    */
    get text(): string;
    getMessageElements(): MixedTextTypedElement[];
    /**
     @deprecated Use getMessageElements instead
     */
    getLinkedText(): MixedTextTypedElement[];
    editText(newText: string): Promise<Message>;
    /*
    * Deletions
    */
    get deleted(): boolean;
    delete(params?: DeleteParameters & {
        preserveFiles?: boolean;
    }): Promise<true | Message>;
    restore(): Promise<Message | undefined>;
    /**
     * Reactions
     */
    get reactions(): MessageReaction[];
    hasUserReaction(reaction: string): boolean;
    toggleReaction(reaction: string): Promise<Message>;
    /*
    * Other
    */
    forward(channelId: string): Promise<Publish.PublishResponse>;
    pin(): Promise<void>;
    /** @deprecated */
    DEPRECATED_report(reason: string): Promise<Signal.SignalResponse>;
    report(reason: string): Promise<Signal.SignalResponse>;
    /**
     * Threads
     */
    getThread(): Promise<ThreadChannel>;
    createThread(text: string, options?: SendTextParams): Promise<CreateThreadResult>;
    /** @deprecated Use createThread() instead. */
    createThreadWithResult(text: string, options?: SendTextParams): Promise<CreateThreadResult>;
    createThreadMessageDraft(config?: Partial<MessageDraftConfig>): Promise<MessageDraft>;
    /** @deprecated Use createThreadMessageDraft() instead. */
    createThreadMessageDraftV2(config?: Partial<MessageDraftConfig>): Promise<MessageDraftV2>;
    removeThread(): Promise<boolean>;
}
declare global {
    interface Array<T> {
        findLastIndex(predicate: (value: T, index: number, obj: T[]) => unknown, thisArg?: any): number;
    }
}
type AddLinkedTextParams = {
    text: string;
    link: string;
    positionInInput: number;
};

declare class MessageDraft {
    get channel(): Channel;
    get value(): string;
    quotedMessage: Message | null | undefined;
    readonly config: MessageDraftConfig;
    files?: FileList | File[] | FileSharing.SendFileParameters<PubNub.PubNubFileParameters>["file"][];
    addQuote(message: Message): void;
    removeQuote(): void;
    addLinkedText(params: AddLinkedTextParams): void;
    removeLinkedText(positionInInput: number): void;
    getMessagePreview(): MixedTextTypedElement[];
    send(params?: SendTextParams): Promise<Publish.PublishResponse>;
    addChangeListener(listener: (p0: MessageDraftState) => void): void;
    removeChangeListener(listener: (p0: MessageDraftState) => void): void;
    insertText(offset: number, text: string): void;
    removeText(offset: number, length: number): void;
    insertSuggestedMention(mention: SuggestedMention, text: string): void;
    addMention(offset: number, length: number, mentionType: MentionType, mentionTarget: string): void;
    removeMention(offset: number): void;
    update(text: string): void;
}

/** @deprecated Use MessageDraft instead. */
declare class MessageDraftV2 {
    get channel(): Channel;
    get value(): string;
    quotedMessage: Message | null | undefined;
    readonly config: MessageDraftConfig;
    files?: FileList | File[] | FileSharing.SendFileParameters<PubNub.PubNubFileParameters>["file"][];
    addQuote(message: Message): void;
    removeQuote(): void;
    addLinkedText(params: AddLinkedTextParams): void;
    removeLinkedText(positionInInput: number): void;
    getMessagePreview(): MixedTextTypedElement[];
    send(params?: SendTextParams): Promise<Publish.PublishResponse>;
    addChangeListener(listener: (p0: MessageDraftState) => void): void;
    removeChangeListener(listener: (p0: MessageDraftState) => void): void;
    insertText(offset: number, text: string): void;
    removeText(offset: number, length: number): void;
    insertSuggestedMention(mention: SuggestedMention, text: string): void;
    addMention(offset: number, length: number, mentionType: MentionType, mentionTarget: string): void;
    removeMention(offset: number): void;
    update(text: string): void;
}

declare class MessageDraftV1 {
    value: string;
    readonly textLinks: TextLink[];
    quotedMessage: Message | null | undefined;
    readonly config: MessageDraftConfig;
    files?: FileList | File[] | { name: string; type: string; data: any }[];
    onChange(text: string): Promise<{
        users: {
            nameOccurrenceIndex: number;
            suggestedUsers: User[];
        };
        channels: {
            channelOccurrenceIndex: number;
            suggestedChannels: Channel[];
        };
    }>;
    addMentionedUser(user: User, nameOccurrenceIndex: number): void;
    addReferencedChannel(channel: Channel, channelNameOccurrenceIndex: number): void;
    removeReferencedChannel(channelNameOccurrenceIndex: number): void;
    removeMentionedUser(nameOccurrenceIndex: number): void;
    getHighlightedMention(selectionStart: number): {
        mentionedUser: User | null;
        nameOccurrenceIndex: number;
    };
    addLinkedText(params: AddLinkedTextParams): void;
    removeLinkedText(positionInInput: number): void;
    getMessagePreview(): MixedTextTypedElement[];
    addQuote(message: Message): void;
    removeQuote(): void;
    send(params?: MessageDraftOptions): Promise<unknown>;
}

declare class MessageDraftState {
    private constructor();
    get messageElements(): Array<MixedTextTypedElement>;
    get suggestedMentions(): Promise<Array<SuggestedMention>>;
}

declare class SuggestedMention {
    offset: number;
    replaceFrom: string;
    replaceWith: string;
    type: MentionType;
    target: string;
}

type ChannelFields = Pick<Channel, "id" | "name" | "custom" | "description" | "updated" | "status" | "type">;
declare class Channel {
    protected chat: Chat;
    readonly id: string;
    readonly name?: string;
    readonly custom?: AppContext.CustomData;
    readonly description?: string;
    readonly updated?: string;
    readonly status?: string;
    readonly type?: ChannelType;
    /*
    * CRUD
    */
    update(data: Omit<ChannelFields, "id">): Promise<Channel>;
    delete(): Promise<true>;
    /*
    * Updates
    */
    static streamUpdatesOn(channels: Channel[], callback: (channels: Channel[]) => unknown): () => void;
    /** @deprecated Use onUpdated and onDeleted instead. */
    streamUpdates(callback: (channel: Channel | null) => unknown): () => void;
    onUpdated(callback: (channel: Channel) => void): () => void;
    onDeleted(callback: () => void): () => void;
    sendText(text: string, options?: SendTextParams): Promise<Publish.PublishResponse>;
    /** @deprecated Use sendText(text, SendTextParams) for simple messages or MessageDraft for rich composition. */
    sendTextLegacy(text: string, options?: SendTextOptionParams): Promise<Publish.PublishResponse>;
    forwardMessage(message: Message): Promise<Publish.PublishResponse>;
    startTyping(): Promise<Signal.SignalResponse | undefined>;
    stopTyping(): Promise<Signal.SignalResponse | undefined>;
    /** @deprecated Use onTypingChanged() instead. */
    getTyping(callback: (typingUserIds: string[]) => unknown): () => void;
    onTypingChanged(callback: (typingUserIds: string[]) => void): () => void;
    /*
    * Streaming messages
    */
    /** @deprecated Use onMessageReceived() instead. */
    connect(callback: (message: Message) => void): () => void;
    onMessageReceived(callback: (message: Message) => void): () => void;
    emitCustomEvent(payload: CustomEventPayload, options?: CustomEventEmitOptions): Promise<Publish.PublishResponse>;
    onCustomEvent(callback: (event: CustomEventData) => void, options?: CustomEventListenOptions): () => void;
    /*
    * Presence
    */
    whoIsPresent(params?: { limit?: number; offset?: number }): Promise<string[]>;
    isPresent(userId: string): Promise<boolean>;
    /** @deprecated Use onPresenceChanged() instead. */
    streamPresence(callback: (userIds: string[]) => unknown): Promise<() => void>;
    onPresenceChanged(callback: (userIds: string[]) => void): () => void;
    /*
    * Messages
    */
    getHistory(params?: {
        startTimetoken?: string;
        endTimetoken?: string;
        count?: number;
    }): Promise<{
        messages: Message[];
        isMore: boolean;
    }>;
    getMessage(timetoken: string): Promise<Message>;
    join(params?: {
        status?: string;
        type?: string;
        custom?: AppContext.CustomData;
    }): Promise<Membership>;
    leave(): Promise<boolean>;
    getMembers(params?: Omit<AppContext.GetMembersParameters, "channel" | "include">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        members: Membership[];
    }>;
    hasMember(userId: string): Promise<boolean>;
    getMember(userId: string): Promise<Membership | null>;
    invite(user: User): Promise<Membership>;
    inviteMultiple(users: User[]): Promise<Membership[]>;
    getInvitees(params?: Omit<AppContext.GetMembersParameters, "channel" | "include">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        members: Membership[];
    }>;
    pinMessage(message: Message): Promise<Channel>;
    unpinMessage(): Promise<Channel>;
    getPinnedMessage(): Promise<Message | null>;
    createMessageDraft(config?: Partial<MessageDraftConfig>): MessageDraft;
    /**
     * @deprecated Use createMessageDraft() instead.
     */
    createMessageDraftV2(config?: Partial<MessageDraftConfig>): MessageDraftV2;
    createMessageDraftV1(config?: Partial<MessageDraftConfig>): MessageDraftV1;
    /** @deprecated Use MessageDraft with addChangeListener for suggestions instead. */
    getUserSuggestions(text: string, options?: { limit: number }): Promise<Membership[]>;
    registerForPush(): Promise<void>;
    unregisterFromPush(): Promise<void>;
    fetchReadReceipts(params?: Omit<AppContext.GetMembersParameters, "channel" | "include">): Promise<ReadReceiptsResponse>;
    /** @deprecated Use onReadReceiptReceived instead. */
    streamReadReceipts(callback: (receipts: {
        [key: string]: string[];
    }) => void): Promise<() => void>;
    onReadReceiptReceived(callback: (receipt: ReadReceipt) => void): () => void;
    getFiles(params?: Omit<FileSharing.ListFilesParameters, "channel">): Promise<{
        files: {
            name: string;
            id: string;
            url: string;
        }[];
        next: string;
        total: number;
    }>;
    deleteFile(params: {
        id: string;
        name: string;
    }): Promise<FileSharing.DeleteFileResponse>;
    /**
     * Moderation restrictions
     */
    setRestrictions(user: User, params: {
        ban?: boolean;
        mute?: boolean;
        reason?: string;
    }): Promise<void>;
    getUserRestrictions(user: User): Promise<{
        ban: boolean;
        mute: boolean;
        reason: string | number | boolean | undefined;
    }>;
    getUsersRestrictions(params?: Pick<AppContext.GetMembersParameters, "limit" | "page" | "sort">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        restrictions: {
            ban: boolean;
            mute: boolean;
            reason: string | number | boolean | undefined;
            userId: string;
        }[];
    }>;
    /**
     * Reported messages
     */
    getMessageReportsHistory(params?: {
        startTimetoken?: string;
        endTimetoken?: string;
        count?: number;
    }): Promise<{
        events: Event<"report">[];
        isMore: boolean;
    }>;
    /** @deprecated Use onMessageReported instead. */
    streamMessageReports(callback: (event: Event<"report">) => void): () => void;
    onMessageReported(callback: (report: MessageReport) => void): () => void;
}
type ChatConfig = {
    saveDebugLog: boolean;
    typingTimeout: number;
    storeUserActivityInterval: number;
    storeUserActivityTimestamps: boolean;
    pushNotifications: {
        sendPushes: boolean;
        deviceToken?: string;
        deviceGateway: "apns2" | "gcm";
        apnsTopic?: string;
        apnsEnvironment: "development" | "production";
    };
    rateLimitFactor: number;
    rateLimitPerChannel: {
        [key in ChannelType]: number;
    };
    errorLogger?: ErrorLoggerImplementation;
    customPayloads: {
        getMessagePublishBody?: (m: TextMessageContent, channelId: string) => any;
        getMessageResponseBody?: (m: MessageDTOParams) => TextMessageContent;
        editMessageActionName?: string;
        deleteMessageActionName?: string;
        reactionsActionName?: string;
    };
    emitReadReceiptEvents?: {
        [key in ChannelType]?: boolean;
    };
    authKey?: string;
    syncMutedUsers?: boolean;
};
type ChatConstructor = Partial<ChatConfig> & PubNub.PubNubConfiguration;
declare class Chat {
    readonly sdk: PubNub;
    readonly config: ChatConfig;
    private user;
    static init(params: ChatConstructor): Promise<Chat>;
    /** @deprecated Use channel.emitCustomEvent() for custom events. */
    emitEvent(event: EmitEventParams): Promise<Signal.SignalResponse>;
    /** @deprecated Use the corresponding method on the entity (channel or user) instead, e.g. channel.onTypingChanged(), user.onMentioned(). */
    listenForEvents<T extends EventType>(event: GenericEventParams<T> & {
        callback: (event: Event<T>) => unknown;
    }): () => void;
    getEventsHistory(params: {
        channel: string;
        startTimetoken?: string;
        endTimetoken?: string;
        count?: number;
    }): Promise<{
        events: Event<any>[];
        isMore: boolean;
    }>;
    get mutedUsersManager(): MutedUsersManager;
    /**
     * Current user
     */
    get currentUser(): User;
    /**
     * Users
     */
    getUser(id: string): Promise<User | null>;
    createUser(id: string, data: Omit<UserFields, "id">): Promise<User>;
    updateUser(id: string, data: Omit<UserFields, "id">): Promise<User>;
    deleteUser(id: string): Promise<true>;
    getUsers(params?: Omit<AppContext.GetAllMetadataParameters<AppContext.UUIDMetadataObject<AppContext.CustomData>>, "include">): Promise<{
        users: User[];
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
    }>;
    /**
     *  Channels
     */
    getChannel(id: string): Promise<Channel | null>;
    updateChannel(id: string, data: Omit<ChannelFields, "id">): Promise<Channel>;
    getChannels(params?: Omit<AppContext.GetAllMetadataParameters<AppContext.ChannelMetadataObject<AppContext.CustomData>>, "include">): Promise<{
        channels: Channel[];
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
    }>;
    deleteChannel(id: string): Promise<true>;
    /**
     * Channel types
     */
    createPublicConversation({ channelId, channelData }?: {
        channelId?: string;
        channelData?: AppContext.ChannelMetadata<AppContext.CustomData>;
    }): Promise<Channel>;
    /**
     *  Presence
     */
    wherePresent(id: string): Promise<string[]>;
    whoIsPresent(id: string, params?: { limit?: number; offset?: number }): Promise<string[]>;
    isPresent(userId: string, channelId: string): Promise<boolean>;
    createDirectConversation({ user, channelId, channelData, membershipData }: {
        user: User;
        channelId?: string;
        channelData?: AppContext.ChannelMetadata<AppContext.CustomData>;
        membershipData?: Omit<AppContext.SetMembershipsParameters<AppContext.CustomData>, "channels" | "include" | "filter"> & {
            custom?: AppContext.CustomData;
        };
    }): Promise<{
        channel: Channel;
        hostMembership: Membership;
        inviteeMembership: Membership;
    }>;
    createGroupConversation({ users, channelId, channelData, membershipData }: {
        users: User[];
        channelId?: string;
        channelData?: AppContext.ChannelMetadata<AppContext.CustomData>;
        membershipData?: Omit<AppContext.SetMembershipsParameters<AppContext.CustomData>, "channels" | "include" | "filter"> & {
            custom?: AppContext.CustomData;
        };
    }): Promise<{
        channel: Channel;
        hostMembership: Membership;
        inviteesMemberships: Membership[];
    }>;
    registerPushChannels(channels: string[]): Promise<void>;
    unregisterPushChannels(channels: string[]): Promise<void>;
    unregisterAllPushChannels(): Promise<void>;
    getPushChannels(): Promise<string[]>;
    downloadDebugLog(): void;
    getCurrentUserMentions(params?: {
        startTimetoken?: string;
        endTimetoken?: string;
        count?: number;
    }): Promise<{
        mentions: UserMention[];
        /** @deprecated Use `mentions` instead. */
        enhancedMentionsData: UserMentionData[];
        isMore: boolean;
    }>;
    getUnreadMessagesCounts(params?: Omit<AppContext.GetMembershipsParameters, "include">): Promise<{
        channel: Channel;
        membership: Membership;
        count: number;
    }[]>;
    fetchUnreadMessagesCounts(params?: Omit<AppContext.GetMembershipsParameters, "include">): Promise<{
        countsByChannel: Array<{
            channel: Channel;
            membership: Membership;
            count: number;
        }>;
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
    }>;
    markAllMessagesAsRead(params?: Omit<AppContext.GetMembershipsParameters, "include">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        memberships: Membership[];
    } | undefined>;
    /**
     * Moderation restrictions
     */
    setRestrictions(userId: string, channelId: string, params: {
        ban?: boolean;
        mute?: boolean;
        reason?: string;
    }): Promise<void>;
    /** @deprecated Use MessageDraft with addChangeListener for suggestions instead. */
    getUserSuggestions(text: string, options?: { limit: number }): Promise<User[]>;
    /** @deprecated Use MessageDraft with addChangeListener for suggestions instead. */
    getChannelSuggestions(text: string, options?: { limit: number }): Promise<Channel[]>;
    /**
     * Channel group
     */
    getChannelGroup(id: string): ChannelGroup;
    removeChannelGroup(id: String): Promise<any>;

    /**
     * Add a listener for connection status changes
     * @param callback Function to be called when connection status changes
     * @returns Function to remove the listener
     */
    addConnectionStatusListener(callback: (status: ConnectionStatus) => void): () => void;

    /**
     * Reconnect all subscriptions
     */
    reconnectSubscriptions(): Promise<void>;

    /**
     * Disconnect all subscriptions
     */
    disconnectSubscriptions(): Promise<void>;
}
declare class ThreadMessage extends Message {
    readonly parentChannelId: string;
    static streamUpdatesOn(threadMessages: ThreadMessage[], callback: (threadMessages: ThreadMessage[]) => unknown): () => void;
    pinToParentChannel(): Promise<Channel>;
    unpinFromParentChannel(): Promise<Channel>;
    onThreadMessageUpdated(callback: (message: ThreadMessage) => void): () => void;
    editText(newText: string): Promise<ThreadMessage>;
    restore(): Promise<ThreadMessage | undefined>;
    toggleReaction(reaction: string): Promise<ThreadMessage>;
}
declare class ThreadChannel extends Channel {
    readonly parentChannelId: string;
    pinMessage(message: ThreadMessage): Promise<ThreadChannel>;
    unpinMessage(): Promise<ThreadChannel>;
    pinMessageToParentChannel(message: ThreadMessage): Promise<Channel>;
    unpinMessageFromParentChannel(): Promise<Channel>;
    onThreadMessageReceived(callback: (message: ThreadMessage) => void): () => void;
    onThreadChannelUpdated(callback: (threadChannel: ThreadChannel) => void): () => void;
    getMessage(timetoken: string): Promise<ThreadMessage>;
    getHistory(params?: {
        startTimetoken?: string;
        endTimetoken?: string;
        count?: number;
    }): Promise<{
        messages: ThreadMessage[];
        isMore: boolean;
    }>;
    update(data: Omit<ChannelFields, "id">): Promise<ThreadChannel>;
    getPinnedMessage(): Promise<ThreadMessage | null>;
    delete(): Promise<true>;
}
declare class TimetokenUtils {
    static unixToTimetoken(unixTime: string | number): number;
    static timetokenToUnix(timetoken: string | number): number;
    static timetokenToDate(timetoken: string | number): Date;
    static dateToTimetoken(date: Date): number;
}
declare class CryptoUtils {
    static decrypt({ chat, message, decryptor }: {
        chat: Chat;
        message: Message;
        decryptor: (encryptedContent: string) => TextMessageContent;
    }): Message;
}

declare class QuotedMessage {
    get timetoken(): string;
    get userId(): string;
    get text(): string;
    getMessageElements(): MixedTextTypedElement[];
}

declare class MutedUsersManager {
    get mutedUsers(): string[];
    muteUser(userId: string): Promise<any>;
    unmuteUser(userId: string): Promise<any>;
}

declare class ChannelGroup {
    get id(): string
    addChannels(channels: Channel[]): Promise<any>;
    addChannelIdentifiers(ids: string[]): Promise<any>;
    removeChannels(channels: Channel[]): Promise<any>;
    removeChannelIdentifiers(ids: string[]): Promise<any>;
    /** @deprecated Use onMessageReceived instead. */
    connect(callback: (message: Message) => void): () => void;
    onMessageReceived(callback: (message: Message) => void): () => void;
    whoIsPresent(params?: { limit?: number; offset?: number }): Promise<{ [key: string]: string[] }>
    onPresenceChanged(callback: (presenceByChannels: { [key: string]: string[] }) => void): () => void;
    /** @deprecated Use onPresenceChanged instead. */
    streamPresence(callback: (presenceByChannels: {
        [key: string]: string[];
    }) => unknown): Promise<() => void>;
    listChannels(params?: Omit<AppContext.GetAllMetadataParameters<AppContext.ChannelMetadataObject<AppContext.CustomData>>, "include">): Promise<{
        channels: Channel[];
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
    }>;
}

declare class ConnectionStatus {
    readonly category: ConnectionStatusCategory;
    readonly exception?: PubNub.PubNubError; // PubNub.PubNubError is the JavaScript equivalent of Kotlin's PubNubException
}

declare const MESSAGE_THREAD_ID_PREFIX = "PUBNUB_INTERNAL_THREAD";
declare const INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_";
declare const INTERNAL_ADMIN_CHANNEL = "PUBNUB_INTERNAL_ADMIN_CHANNEL";
declare const ERROR_LOGGER_KEY_PREFIX = "PUBNUB_INTERNAL_ERROR_LOGGER";
declare const CryptoModule: typeof PubNub.CryptoModule;
export {
    ChatConfig, Chat, ChannelFields, Channel, ChannelGroup,
    ConnectionStatusCategory, ConnectionStatus,
    UserFields, User,
    MessageFields, Message,
    MembershipFields, Membership,
    ThreadChannel, ThreadMessage,
    MessageDraft, MessageDraftV1, MessageDraftV2,
    MessageDraftOptions, MessageDraftState, SuggestedMention,
    EventFields, Event,
    ChannelType, MessageType, MessageActionType, TextMessageContent,
    EventParams, EventPayloads, EmitEventParams, EventType, GenericEventParams,
    Mention, Invite,
    MessageActions, MessageReaction,
    DeleteParameters,
    MessageMentionedUsers, MessageReferencedChannels,
    SendTextParams, SendTextOptionParams,
    EnhancedMessageEvent,
    MessageDTOParams, ThreadMessageDTOParams,
    MembershipResponse, OptionalAllBut,
    ChannelDTOParams, ThreadChannelDTOParams,
    MessageDraftConfig,
    TextLink, GetLinkedTextParams,
    PayloadForTextTypes, TextTypes, MentionType, TextTypeElement, MixedTextTypedElement,
    ErrorLoggerSetParams, ErrorLoggerImplementation,
    UserMention,
    ChannelMentionData, ThreadMentionData, UserMentionData,
    TimetokenUtils, CryptoUtils,
    MESSAGE_THREAD_ID_PREFIX, INTERNAL_MODERATION_PREFIX, INTERNAL_ADMIN_CHANNEL, ERROR_LOGGER_KEY_PREFIX,
    CryptoModule,
    CreateThreadResult,
    ReadReceipt, ReadReceiptsResponse,
    MessageReport,
    CustomEventData, CustomEventEmitOptions, CustomEventListenOptions
};
