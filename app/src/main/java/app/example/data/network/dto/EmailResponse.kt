package app.example.data.network.dto

import app.example.data.model.Email
import kotlinx.serialization.Serializable

/**
 * DTO representing an email as returned by the API.
 *
 * Maps to the `Email` schema defined in the OpenAPI spec at
 * https://email-demo.gohk.xyz/openapi.json
 */
@Serializable
data class EmailDto(
    val id: String,
    val subject: String,
    val body: String,
    val sender: String,
    val senderEmail: String,
    val recipients: List<String>,
    val timestamp: String,
    val status: String,
)

/** API response wrapper for a list of emails (used for inbox and drafts). */
@Serializable
data class EmailListResponse(
    val success: Boolean,
    val data: List<EmailDto>,
    val count: Int? = null,
)

/** API response wrapper for a single email (used for send/draft creation). */
@Serializable
data class SingleEmailResponse(
    val success: Boolean,
    val data: EmailDto,
    val message: String? = null,
)

/** API response wrapper for delete operations. */
@Serializable
data class DeleteEmailResponse(
    val success: Boolean,
    val message: String? = null,
)

/** Maps an [EmailDto] to the domain [Email] model. */
fun EmailDto.toDomain(): Email =
    Email(
        id = id,
        subject = subject,
        body = body,
        sender = sender,
        senderEmail = senderEmail,
        recipients = recipients,
        timestamp = timestamp,
        status = status,
    )
