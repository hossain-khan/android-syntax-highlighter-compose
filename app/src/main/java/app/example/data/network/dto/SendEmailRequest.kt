package app.example.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Request body for sending an email via `POST /api/emails/send`.
 *
 * See https://email-demo.gohk.xyz/openapi.json for the full schema.
 */
@Serializable
data class SendEmailRequest(
    val subject: String,
    val body: String,
    val sender: String,
    val senderEmail: String,
    val recipients: List<String>,
)
