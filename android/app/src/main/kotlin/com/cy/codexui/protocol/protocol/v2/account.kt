package com.cy.codexui.protocol.protocol.v2

/**
 * `account/…` — signing in, signing out, and the account's own read models.
 *
 * Mirrors `schema/typescript/v2/LoginAccountParams.ts`, which is an internally-tagged union over six
 * sign-in methods. The phone only starts two of them (ChatGPT and an API key) but the whole union is
 * carried, because [LoginAccountResponse] has to be decoded for whichever one the user picked and a
 * half-modelled union silently decodes the wrong variant.
 */

/** `account/login/start` params. The `type` tag decides which variant is on the wire. */
sealed interface LoginAccountParams {
    /** Paste an API key; completes immediately. */
    data class ApiKey(val apiKey: String) : LoginAccountParams

    /** Browser/device sign-in: the server returns a URL (or a code) and waits. */
    data class Chatgpt(
        val appBrand: LoginAppBrand = LoginAppBrand.Codex,
        val useHostedLoginSuccessPage: Boolean = true,
        val codexStreamlinedLogin: Boolean = false,
    ) : LoginAccountParams

    /** Headless sign-in: show `userCode`, send the user to `verificationUrl`. */
    data object ChatgptDeviceCode : LoginAccountParams

    /** Re-hydrate a session from tokens the host already holds (e.g. an Android account). */
    data class ChatgptAuthTokens(
        val accessToken: String,
        val chatgptAccountId: String,
        val chatgptPlanType: String? = null,
    ) : LoginAccountParams

    data class AmazonBedrock(
        val apiKey: String,
        val region: String,
    ) : LoginAccountParams

    data class AmazonBedrockAccessKeys(
        val accessKeyId: String,
        val secretAccessKey: String,
        val region: String,
        val sessionToken: String? = null,
    ) : LoginAccountParams
}

/** Which product the browser sign-in page should be branded as. */
enum class LoginAppBrand(val wire: String) {
    Codex("codex"),
    Chatgpt("chatgpt"),
}

/** `account/login/start` response. */
sealed interface LoginAccountResponse {
    /** The key was accepted; there is nothing to wait for. */
    data object ApiKey : LoginAccountResponse

    /** Open [authUrl]; completion arrives as `account/login/completed` with this [loginId]. */
    data class Chatgpt(val loginId: String, val authUrl: String) : LoginAccountResponse

    /** Headless variant: show [userCode] and send the user to [verificationUrl]. */
    data class ChatgptDeviceCode(
        val loginId: String,
        val userCode: String,
        val verificationUrl: String,
    ) : LoginAccountResponse

    data object ChatgptAuthTokens : LoginAccountResponse

    data object AmazonBedrock : LoginAccountResponse

    /** The server answered with a variant this client does not model. */
    data object Unknown : LoginAccountResponse
}

/** `account/login/cancel`. */
data class CancelLoginAccountParams(val loginId: String)

/** `account/login/completed`. */
data class AccountLoginCompletedNotification(
    val success: Boolean,
    val loginId: String? = null,
    val error: String? = null,
)

/** `account/workspaceMessages/read` response. */
data class WorkspaceMessagesResponse(
    val featureEnabled: Boolean = false,
    val messages: List<WorkspaceMessage> = emptyList(),
)

data class WorkspaceMessage(
    val id: String,
    val title: String,
    val body: String = "",
    val severity: DiagnosticSeverity = DiagnosticSeverity.Info,
)

/** `account/rateLimitResetCredit/consume`. */
data class ConsumeRateLimitResetCreditParams(
    val idempotencyKey: String,
    val creditId: String? = null,
)

data class ConsumeRateLimitResetCreditResponse(
    val consumed: Boolean = false,
    val message: String? = null,
)

/** `account/sendAddCreditsNudgeEmail`. */
data class SendAddCreditsNudgeEmailParams(val email: String? = null)

/** `account/bedrock/discover` and `account/bedrock/setup`. */
data class BedrockDiscoverResponse(val regions: List<String> = emptyList())

data class BedrockSetupParams(val region: String)

data class BedrockSetupResponse(val configured: Boolean = false)
