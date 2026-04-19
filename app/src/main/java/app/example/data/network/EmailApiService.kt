package app.example.data.network

import app.example.data.network.dto.CreateDraftRequest
import app.example.data.network.dto.DeleteEmailResponse
import app.example.data.network.dto.EmailListResponse
import app.example.data.network.dto.SendEmailRequest
import app.example.data.network.dto.SingleEmailResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API contract for the email demo service.
 *
 * Base URL: https://email-demo.gohk.xyz/
 * Full API spec: https://email-demo.gohk.xyz/openapi.json
 */
interface EmailApiService {
    /**
     * Fetch inbox emails with optional pagination.
     *
     * @param limit  Maximum number of emails to return (default: 50, max: 500).
     * @param offset Pagination offset (default: 0).
     */
    @GET("api/emails/inbox")
    suspend fun getInboxEmails(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): EmailListResponse

    /** Fetch all draft emails. */
    @GET("api/emails/drafts")
    suspend fun getDraftEmails(): EmailListResponse

    /**
     * Send an email (moves it from draft to inbox on the server).
     *
     * Note: Calling this operation may require invalidating local caches
     * in the repository to reflect changes in the inbox.
     *
     * @param request The email content to send.
     */
    @POST("api/emails/send")
    suspend fun sendEmail(
        @Body request: SendEmailRequest,
    ): SingleEmailResponse

    /**
     * Create or update a draft email.
     *
     * Note: Calling this operation may require invalidating local draft
     * caches in the repository to reflect the new or updated draft.
     *
     * @param request The draft email content.
     */
    @POST("api/emails/drafts")
    suspend fun createDraft(
        @Body request: CreateDraftRequest,
    ): SingleEmailResponse

    /**
     * Delete an email from inbox or drafts.
     *
     * @param emailId The UUID of the email to delete.
     */
    @DELETE("api/emails/{emailId}")
    suspend fun deleteEmail(
        @Path("emailId") emailId: String,
    ): DeleteEmailResponse
}
