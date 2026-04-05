package com.retainiq.api

import com.retainiq.api.dto.*
import com.retainiq.service.DecisionService
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@WebFluxTest(DecisionController::class)
@Import(DecisionControllerTest.TestConfig::class)
class DecisionControllerTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @Autowired
    private lateinit var decisionService: DecisionService

    @TestConfiguration
    class TestConfig {
        @Bean
        fun decisionService(): DecisionService = mockk()
    }

    @Test
    fun `POST v1 decide returns 200 with offers`() {
        val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val decisionId = UUID.randomUUID()

        coEvery { decisionService.decide(tenantId, any()) } returns DecideResponse(
            decisionId = decisionId,
            subscriber = SubscriberSummaryDto("postpaid", 0.73, "HIGH", 730),
            offers = listOf(
                OfferDto(1, "VAS-STREAM-PLUS", "StreamPlus", 0.68, 12.5, "Try StreamPlus", null, null)
            ),
            action = "present_offers",
            degraded = false,
            confidence = "HIGH",
            latencyMs = 87
        )

        webClient
            .mutateWith(mockUser())
            .mutateWith(csrf())
            .post()
            .uri("/v1/decide")
            .header("X-Tenant-ID", tenantId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"subscriber_id": "sub_123", "channel": "app"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.decision_id").isEqualTo(decisionId.toString())
            .jsonPath("$.offers[0].sku").isEqualTo("VAS-STREAM-PLUS")
            .jsonPath("$.action").isEqualTo("present_offers")
    }
}
