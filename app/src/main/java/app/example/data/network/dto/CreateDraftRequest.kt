package app.example.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Request body for creating or updating a draft via `POST /api/emails/drafts`.
 *
 * Note: When updating an existing draft, the `id` of the draft should be included
 * in the request if supported by the API, otherwise it may create a new draft.
 *
 * See https://email-demo.gohk.xyz/openapi.json for the full schema.
 */
@Serializable
data class CreateDraftRequest(
    val subject: String,
    val body: String,
    val sender: String,
    val senderEmail: String,
    val recipients: List<String>,
)
