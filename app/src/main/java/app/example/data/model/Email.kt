package app.example.data.model

/**
 * Domain model representing an email.
 *
 * This is the model used by presenters and UI layers. It is mapped from the API DTO
 * by [app.example.data.repository.EmailRepositoryImpl].
 */
data class Email(
    val id: String,
    val subject: String,
    val body: String,
    val sender: String,
    val senderEmail: String,
    val recipients: List<String>,
    val timestamp: String,
    val status: String,
)
