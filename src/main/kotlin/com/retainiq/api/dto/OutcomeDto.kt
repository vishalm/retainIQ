/**
 * Data transfer object for the `/v1/outcome` feedback endpoint.
 */
package com.retainiq.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * Inbound request for recording the outcome of a previously presented decision.
 *
 * @property decisionId the [Decision][com.retainiq.domain.Decision] this outcome refers to
 * @property offerSku the SKU of the offer the subscriber responded to
 * @property outcome result string: "accepted", "declined", or "no_response"
 * @property revenueDelta optional incremental revenue attributed to this outcome
 * @property churnPrevented optional flag indicating whether churn was prevented
 */
data class OutcomeRequest(
    @field:NotNull
    @JsonProperty("decision_id")
    val decisionId: UUID,

    @field:NotBlank
    @JsonProperty("offer_sku")
    val offerSku: String,

    @field:NotBlank
    val outcome: String,

    @JsonProperty("revenue_delta")
    val revenueDelta: Double? = null,

    @JsonProperty("churn_prevented")
    val churnPrevented: Boolean? = null
)
