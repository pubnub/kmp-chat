/// <reference types="pubnub" />
import PubNub from "pubnub";
import { GetMembershipsParametersv2, GetChannelMembersParameters, ObjectCustom, SetMembershipsParameters, ChannelMetadataObject, PublishParameters, SendFileParameters } from "pubnub";
type MembershipFields = Pick<Membership, "channel" | "user" | "custom" | "updated" | "eTag">;
declare class Membership {
    private chat;
    readonly channel: Channel;
    readonly user: User;
    readonly custom: ObjectCustom | null | undefined;
    readonly updated: string;
    readonly eTag: string;
    update({ custom }: {
        custom: ObjectCustom;
    }): Promise<Membership>;
    /*
    * Updates
    */
    static streamUpdatesOn(memberships: Membership[], callback: (memberships: Membership[]) => unknown): () => void;
    streamUpdates(callback: (membership: Membership) => unknown): () => void;
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
    readonly custom?: ObjectCustom;
    readonly status?: string;
    readonly type?: string;
    readonly updated?: string;
    readonly lastActiveTimestamp?: number;
    get active(): boolean;
    /*
    * CRUD
    */
    update(data: Omit<UserFields, "id">): Promise<User>;
    delete(options?: DeleteParameters): Promise<true | User>;
    /*
    * Updates
    */
    static streamUpdatesOn(users: User[], callback: (users: User[]) => unknown): () => void;
    streamUpdates(callback: (user: User) => unknown): () => void;
    /*
    * Presence
    */
    wherePresent(): Promise<string[]>;
    isPresentOn(channelId: string): Promise<boolean>;
    /*
    * Memberships
    */
    getMemberships(params?: Omit<GetMembershipsParametersv2, "include" | "uuid">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        memberships: Membership[];
    }>;
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
    getChannelsRestrictions(params?: Pick<PubNub.GetChannelMembersParameters, "limit" | "page" | "sort">): Promise<{
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
    DEPRECATED_report(reason: string): Promise<PubNub.SignalResponse>;
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
type TypingEventParams = {
    type: "typing";
    channel: string;
};
type ReportEventParams = {
    type: "report";
    channel: string;
};
type ReceiptEventParams = {
    type: "receipt";
    channel: string;
};
type MentionEventParams = {
    type: "mention";
    user: string;
};
type InviteEventParams = {
    type: "invite";
    channel: string;
};
type ModerationEventParams = {
    type: "moderation";
    channel: string;
};
type CustomEventParams = {
    type: "custom";
    method: "signal" | "publish";
    channel: string;
};
type EventParams = {
    typing: TypingEventParams;
    report: ReportEventParams;
    receipt: ReceiptEventParams;
    mention: MentionEventParams;
    invite: InviteEventParams;
    custom: CustomEventParams;
    moderation: ModerationEventParams;
};
type TypingEventPayload = {
    value: boolean;
};
type ReportEventPayload = {
    text?: string;
    reason: string;
    reportedMessageTimetoken?: string;
    reportedMessageChannelId?: string;
    reportedUserId?: string;
};
type ReceiptEventPayload = {
    messageTimetoken: string;
};
type MentionEventPayload = {
    messageTimetoken: string;
    channel: string;
};
type InviteEventPayload = {
    channelType: ChannelType | "unknown";
    channelId: string;
};
type ModerationEventPayload = {
    channelId: string;
    restriction: "muted" | "banned" | "lifted";
    reason?: string;
};
type CustomEventPayload = any;
type EventPayloads = {
    typing: TypingEventPayload;
    report: ReportEventPayload;
    receipt: ReceiptEventPayload;
    mention: MentionEventPayload;
    invite: InviteEventPayload;
    moderation: ModerationEventPayload;
    custom: CustomEventPayload;
};
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
type MessageDraftOptions = Omit<PublishParameters, "message" | "channel">;
type SendTextOptionParams = Omit<PublishParameters, "message" | "channel"> & {
    mentionedUsers?: MessageMentionedUsers;
    referencedChannels?: MessageReferencedChannels;
    textLinks?: TextLink[];
    quotedMessage?: Message;
    files?: FileList | File[] | SendFileParameters["file"][];
};
type EnhancedMessageEvent = PubNub.MessageEvent & {
    userMetadata?: {
        [key: string]: any;
    };
};
type MessageDTOParams = PubNub.FetchMessagesResponse["channels"]["channel"][0] | EnhancedMessageEvent;
type ThreadMessageDTOParams = MessageDTOParams & {
    parentChannelId: string;
};
type MembershipResponse = Awaited<ReturnType<User["getMemberships"]>>;
type OptionalAllBut<T, K extends keyof T> = Partial<T> & Pick<T, K>;
type ChannelDTOParams = OptionalAllBut<ChannelMetadataObject<ObjectCustom>, "id"> & {
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
type ChannelMentionData = {
    event: Event<"mention">;
    channelId: string;
    message: Message;
    userId: string;
};
type ThreadMentionData = {
    event: Event<"mention">;
    parentChannelId: string;
    threadChannelId: string;
    message: Message;
    userId: string;
};
type UserMentionData = ChannelMentionData | ThreadMentionData;
type MessageFields = Pick<Message, "timetoken" | "content" | "channelId" | "userId" | "actions" | "meta">;
declare class Message {
    protected chat: Chat;
    readonly timetoken: string;
    readonly content: TextMessageContent;
    readonly channelId: string;
    readonly userId: string;
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
    get quotedMessage(): any;
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
    streamUpdates(callback: (message: Message) => unknown): () => void;
    /*
    * Message text
    */
    get text(): string;
    getMessageElements(): MixedTextTypedElement[];
    /**
     @deprecated use getMessageElements instead
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
    get reactions(): {
        [value: string]: {
            uuid: string;
            actionTimetoken: string | number;
        }[];
    };
    hasUserReaction(reaction: string): boolean;
    toggleReaction(reaction: string): Promise<Message>;
    /*
    * Other
    */
    forward(channelId: string): Promise<PubNub.PublishResponse>;
    pin(): Promise<void>;
    /** @deprecated */
    DEPRECATED_report(reason: string): Promise<PubNub.SignalResponse>;
    report(reason: string): Promise<PubNub.SignalResponse>;
    /**
     * Threads
     */
    getThread(): Promise<ThreadChannel>;
    createThread(): Promise<ThreadChannel>;
    removeThread(): Promise<[
        {
            data: {};
        },
        true | Channel
    ]>;
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

declare class MessageDraftV2 {
    get channel: Channel;
    get value: string;
    quotedMessage: Message | undefined;
    readonly config: MessageDraftConfig;
    files?: FileList | File[] | SendFileParameters["file"][];
    addQuote(message: Message): void;
    removeQuote(): void;
    addLinkedText(params: AddLinkedTextParams): void;
    removeLinkedText(positionInInput: number): void;
    getMessagePreview(): MixedTextTypedElement[];
    send(params?: MessageDraftOptions): Promise<PubNub.PublishResponse>;
    addChangeListener(listener:(messageElements: MixedTextTypedElement[], suggestedMentions:Promise<SuggestedMention[]>) => unknown): void;
    removeChangeListener(listener:(messageElements: MixedTextTypedElement[], suggestedMentions:Promise<SuggestedMention[]>) => unknown): void;
    insertText(offset: number, text: string): void;
    removeText(offset: number, length: number): void;
    removeText(offset: number, length: number): void;
    insertSuggestedMention(mention: SuggestedMention, text: string): void;
    addMention(offset: number, length: number, mentionType: TextTypes, mentionTarget: string): void;
    removeMention(offset: number): void;
    update(text: string): void;
}

declare class SuggestedMention {
    offset: number;
    replaceFrom: string;
    replaceWith: string;
    type: TextTypes;
    target: string;
}

declare class MessageDraft {
    private chat;
    value: string;
    quotedMessage: Message | undefined;
    readonly config: MessageDraftConfig;
    files?: FileList | File[] | SendFileParameters["file"][];
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
    send(params?: MessageDraftOptions): Promise<unknown>;
    getHighlightedMention(selectionStart: number): {
        mentionedUser: null;
        nameOccurrenceIndex: number;
    } | {
        mentionedUser: User;
        nameOccurrenceIndex: number;
    };
    addLinkedText(params: AddLinkedTextParams): void;
    removeLinkedText(positionInInput: number): void;
    getMessagePreview(): MixedTextTypedElement[];
    addQuote(message: Message): void;
    removeQuote(): void;
}
type ChannelFields = Pick<Channel, "id" | "name" | "custom" | "description" | "updated" | "status" | "type">;
declare class Channel {
    protected chat: Chat;
    readonly id: string;
    readonly name?: string;
    readonly custom?: ObjectCustom;
    readonly description?: string;
    readonly updated?: string;
    readonly status?: string;
    readonly type?: ChannelType;
    /*
    * CRUD
    */
    update(data: Omit<ChannelFields, "id">): Promise<Channel>;
    delete(options?: DeleteParameters): Promise<true | Channel>;
    /*
    * Updates
    */
    static streamUpdatesOn(channels: Channel[], callback: (channels: Channel[]) => unknown): () => void;
    streamUpdates(callback: (channel: Channel) => unknown): () => void;
    sendText(text: string, options?: SendTextOptionParams): Promise<unknown>;
    forwardMessage(message: Message): Promise<PubNub.PublishResponse>;
    startTyping(): Promise<PubNub.SignalResponse | undefined>;
    stopTyping(): Promise<PubNub.SignalResponse | undefined>;
    getTyping(callback: (typingUserIds: string[]) => unknown): () => void;
    /*
    * Streaming messages
    */
    connect(callback: (message: Message) => void): () => void;
    /*
    * Presence
    */
    whoIsPresent(): Promise<string[]>;
    isPresent(userId: string): Promise<boolean>;
    streamPresence(callback: (userIds: string[]) => unknown): Promise<() => void>;
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
    join(callback: (message: Message) => void, params?: Omit<SetMembershipsParameters<ObjectCustom>, "channels" | "include" | "filter"> & {
        custom?: ObjectCustom;
    }): Promise<{
        membership: Membership;
        disconnect: () => void;
    }>;
    leave(): Promise<boolean>;
    getMembers(params?: Omit<GetChannelMembersParameters, "channel" | "include">): Promise<{
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
        status: number;
        members: Membership[];
    }>;
    invite(user: User): Promise<Membership>;
    inviteMultiple(users: User[]): Promise<Membership[]>;
    pinMessage(message: Message): Promise<Channel>;
    unpinMessage(): Promise<Channel>;
    getPinnedMessage(): Promise<Message | null>;
    getUserSuggestions(text: string, options?: {
        limit: number;
    }): Promise<Membership[]>;
    createMessageDraft(config?: Partial<MessageDraftConfig>): MessageDraft;
    createMessageDraftV2(config?: Partial<MessageDraftConfig>): MessageDraftV2;
    registerForPush(): Promise<void>;
    unregisterFromPush(): Promise<void>;
    streamReadReceipts(callback: (receipts: {
        [key: string]: string[];
    }) => unknown): Promise<() => void>;
    getFiles(params?: Omit<PubNub.ListFilesParameters, "channel">): Promise<{
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
    }): Promise<PubNub.DeleteFileResponse>;
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
    getUsersRestrictions(params?: Pick<PubNub.GetChannelMembersParameters, "limit" | "page" | "sort">): Promise<{
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
    streamMessageReports(callback: (event: Event<"report">) => void): () => void;
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
    authKey?: string;
};
type ChatConstructor = Partial<ChatConfig> & PubNub.PubnubConfig;
declare class Chat {
    readonly sdk: PubNub;
    readonly config: ChatConfig;
    private user;
    static init(params: ChatConstructor): Promise<Chat>;
    emitEvent(event: EmitEventParams): Promise<PubNub.SignalResponse>;
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
    deleteUser(id: string, params?: DeleteParameters): Promise<true | User>;
    getUsers(params?: Omit<PubNub.GetAllMetadataParameters, "include">): Promise<{
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
    getChannels(params?: Omit<PubNub.GetAllMetadataParameters, "include">): Promise<{
        channels: Channel[];
        page: {
            next: string | undefined;
            prev: string | undefined;
        };
        total: number | undefined;
    }>;
    deleteChannel(id: string, params?: DeleteParameters): Promise<true | Channel>;
    /**
     * Channel types
     */
    createPublicConversation({ channelId, channelData }?: {
        channelId?: string;
        channelData?: PubNub.ChannelMetadata<PubNub.ObjectCustom>;
    }): Promise<Channel>;
    /**
     *  Presence
     */
    wherePresent(id: string): Promise<string[]>;
    whoIsPresent(id: string): Promise<string[]>;
    isPresent(userId: string, channelId: string): Promise<boolean>;
    createDirectConversation({ user, channelId, channelData, membershipData }: {
        user: User;
        channelId?: string;
        channelData?: PubNub.ChannelMetadata<PubNub.ObjectCustom>;
        membershipData?: Omit<PubNub.SetMembershipsParameters<PubNub.ObjectCustom>, "channels" | "include" | "filter"> & {
            custom?: PubNub.ObjectCustom;
        };
    }): Promise<{
        channel: Channel;
        hostMembership: Membership;
        inviteeMembership: Membership;
    }>;
    createGroupConversation({ users, channelId, channelData, membershipData }: {
        users: User[];
        channelId?: string;
        channelData?: PubNub.ChannelMetadata<PubNub.ObjectCustom>;
        membershipData?: Omit<PubNub.SetMembershipsParameters<PubNub.ObjectCustom>, "channels" | "include" | "filter"> & {
            custom?: PubNub.ObjectCustom;
        };
    }): Promise<{
        channel: Channel;
        hostMembership: Membership;
        inviteesMemberships: Membership[];
    }>;
    getUserSuggestions(text: string, options?: {
        limit: number;
    }): Promise<User[]>;
    getChannelSuggestions(text: string, options?: {
        limit: number;
    }): Promise<Channel[]>;
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
        enhancedMentionsData: UserMentionData[];
        isMore: boolean;
    }>;
    getUnreadMessagesCounts(params?: Omit<GetMembershipsParametersv2, "include">): Promise<{
        channel: Channel;
        membership: Membership;
        count: number;
    }[]>;
    markAllMessagesAsRead(params?: Omit<GetMembershipsParametersv2, "include">): Promise<{
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
}
declare class ThreadMessage extends Message {
    readonly parentChannelId: string;
    static streamUpdatesOn(threadMessages: ThreadMessage[], callback: (threadMessages: ThreadMessage[]) => unknown): () => void;
    pinToParentChannel(): Promise<Channel>;
    unpinFromParentChannel(): Promise<Channel>;
}
declare class ThreadChannel extends Channel {
    readonly parentChannelId: string;
    pinMessage(message: ThreadMessage): Promise<ThreadChannel>;
    unpinMessage(): Promise<ThreadChannel>;
    pinMessageToParentChannel(message: ThreadMessage): Promise<Channel>;
    unpinMessageFromParentChannel(): Promise<Channel>;
    getHistory(params?: {
        startTimetoken?: string;
        endTimetoken?: string;
        count?: number;
    }): Promise<{
        messages: ThreadMessage[];
        isMore: boolean;
    }>;
    delete(options?: DeleteParameters): Promise<true | Channel>;
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
declare const MESSAGE_THREAD_ID_PREFIX = "PUBNUB_INTERNAL_THREAD";
declare const INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_";
declare const INTERNAL_ADMIN_CHANNEL = "PUBNUB_INTERNAL_ADMIN_CHANNEL";
declare const ERROR_LOGGER_KEY_PREFIX = "PUBNUB_INTERNAL_ERROR_LOGGER";
declare const CryptoModule: typeof PubNub.CryptoModule;
export { ChatConfig, Chat, ChannelFields, Channel, UserFields, User, MessageFields, Message, MembershipFields, Membership, ThreadChannel, ThreadMessage, MessageDraft, EventFields, Event, ChannelType, MessageType, MessageActionType, TextMessageContent, EventParams, EventPayloads, EmitEventParams, EventType, GenericEventParams, MessageActions, DeleteParameters, MessageMentionedUsers, MessageReferencedChannels, MessageDraftOptions, SendTextOptionParams, EnhancedMessageEvent, MessageDTOParams, ThreadMessageDTOParams, MembershipResponse, OptionalAllBut, ChannelDTOParams, ThreadChannelDTOParams, MessageDraftConfig, TextLink, GetLinkedTextParams, PayloadForTextTypes, TextTypes, TextTypeElement, MixedTextTypedElement, ErrorLoggerSetParams, ErrorLoggerImplementation, ChannelMentionData, ThreadMentionData, UserMentionData, TimetokenUtils, CryptoUtils, MESSAGE_THREAD_ID_PREFIX, INTERNAL_MODERATION_PREFIX, INTERNAL_ADMIN_CHANNEL, ERROR_LOGGER_KEY_PREFIX, CryptoModule };
