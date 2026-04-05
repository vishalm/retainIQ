/**
 * Data transfer objects for the `/v1/catalog/sync` webhook endpoint.
 */
package com.retainiq.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotEmpty

/**
 * Inbound payload for a VAS catalog sync webhook.
 *
 * @property event event type (e.g. "product.created", "product.updated", "product.retired")
 * @property products list of product DTOs to upsert or retire
 * @property fullSync if true, the product list replaces the entire catalog; otherwise incremental
 */
data class CatalogSyncRequest(
    val event: String,
    @field:NotEmpty
    val products: List<ProductDto>,
    @JsonProperty("full_sync")
    val fullSync: Boolean = false
)

/**
 * Wire representation of a VAS product within a catalog sync payload.
 *
 * @property sku unique stock-keeping unit identifier
 * @property name English display name
 * @property nameAr Arabic display name (required for KSA compliance)
 * @property category product category
 * @property markets list of ISO 3166-1 alpha-2 market codes
 * @property margin operator margin per activation
 * @property eligibilityRules JSON DSL eligibility rules
 * @property bundleWith SKUs that bundle with this product
 * @property incompatibleWith SKUs incompatible with this product
 * @property upgradeFrom SKUs this product upgrades from
 * @property regulatory optional regulatory metadata
 */
data class ProductDto(
    val sku: String,
    val name: String,
    @JsonProperty("name_ar")
    val nameAr: String? = null,
    val category: String,
    val markets: List<String> = emptyList(),
    val margin: Double,
    @JsonProperty("eligibility_rules")
    val eligibilityRules: List<String> = emptyList(),
    @JsonProperty("bundle_with")
    val bundleWith: List<String> = emptyList(),
    @JsonProperty("incompatible_with")
    val incompatibleWith: List<String> = emptyList(),
    @JsonProperty("upgrade_from")
    val upgradeFrom: List<String> = emptyList(),
    val regulatory: ProductRegulatoryDto? = null
)

/**
 * Wire representation of product-level regulatory metadata.
 *
 * @property consentRequired whether explicit subscriber consent is needed
 * @property disclosureText market-code-keyed disclosure text
 * @property coolingOffHours product-specific cooling-off period in hours
 */
data class ProductRegulatoryDto(
    @JsonProperty("consent_required")
    val consentRequired: Boolean = false,
    @JsonProperty("disclosure_text")
    val disclosureText: Map<String, String> = emptyMap(),
    @JsonProperty("cooling_off_hours")
    val coolingOffHours: Int? = null
)

/**
 * Response from the catalog sync endpoint.
 *
 * @property syncId unique identifier for tracking this sync operation
 * @property status always "accepted" on success
 * @property productsReceived number of products in the sync payload
 */
data class CatalogSyncResponse(
    @JsonProperty("sync_id")
    val syncId: String,
    val status: String = "accepted",
    @JsonProperty("products_received")
    val productsReceived: Int
)
