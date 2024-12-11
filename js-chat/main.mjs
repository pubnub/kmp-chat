export * from "../build/dist/js/productionLibrary/pubnub-chat.mjs"
export const INTERNAL_MODERATION_PREFIX = "PUBNUB_INTERNAL_MODERATION_"
export const MESSAGE_THREAD_ID_PREFIX = "PUBNUB_INTERNAL_THREAD";
export const INTERNAL_ADMIN_CHANNEL = "PUBNUB_INTERNAL_ADMIN_CHANNEL";
export const ERROR_LOGGER_KEY_PREFIX = "PUBNUB_INTERNAL_ERROR_LOGGER";

import PubNub from "pubnub"
export let CryptoModule = PubNub.CryptoModule