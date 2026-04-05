package com.retainiq.api

import com.retainiq.api.dto.*
import com.retainiq.domain.UserRole
import com.retainiq.security.TokenService
import com.retainiq.service.TenantConfigService
import com.retainiq.service.UserService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/v1/manage")
class ManagementController(
    private val tenantConfigService: TenantConfigService,
    private val userService: UserService,
    private val tokenService: TokenService
) {

    // ── Authentication ────────────────────────────────────

    @PostMapping("/login")
    suspend fun login(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        val user = userService.authenticate(request.email, request.password)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse(ErrorDetail("invalid_credentials", "Invalid email or password")))

        val token = tokenService.issueUserToken(user)
        return ResponseEntity.ok(LoginResponse(
            accessToken = token,
            expiresIn = 3600,
            user = userService.toResponse(user)
        ))
    }

    // ── Tenant (Telco) Configuration ──────────────────────

    @PostMapping("/tenants")
    suspend fun createTenant(@RequestBody request: CreateTenantRequest): ResponseEntity<TenantResponse> {
        val (response, _) = tenantConfigService.createTenant(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/tenants")
    suspend fun listTenants(): ResponseEntity<List<TenantResponse>> {
        return ResponseEntity.ok(tenantConfigService.listTenants())
    }

    @GetMapping("/tenants/{id}")
    suspend fun getTenant(@PathVariable id: UUID): ResponseEntity<Any> {
        val tenant = tenantConfigService.getTenant(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(ErrorDetail("not_found", "Tenant not found")))
        return ResponseEntity.ok(tenant)
    }

    @PutMapping("/tenants/{id}")
    suspend fun updateTenant(@PathVariable id: UUID, @RequestBody request: UpdateTenantRequest): ResponseEntity<Any> {
        val tenant = tenantConfigService.updateTenant(id, request)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(ErrorDetail("not_found", "Tenant not found")))
        return ResponseEntity.ok(tenant)
    }

    @PostMapping("/tenants/{id}/activate")
    suspend fun activateTenant(@PathVariable id: UUID): ResponseEntity<Any> {
        val tenant = tenantConfigService.activateTenant(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(ErrorDetail("not_found", "Tenant not found")))
        return ResponseEntity.ok(tenant)
    }

    @PostMapping("/tenants/{id}/suspend")
    suspend fun suspendTenant(@PathVariable id: UUID): ResponseEntity<Any> {
        val tenant = tenantConfigService.suspendTenant(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(ErrorDetail("not_found", "Tenant not found")))
        return ResponseEntity.ok(tenant)
    }

    @PostMapping("/tenants/{id}/test-bss")
    suspend fun testBss(@PathVariable id: UUID): ResponseEntity<BssTestResult> {
        return ResponseEntity.ok(tenantConfigService.testBssConnection(id))
    }

    @PostMapping("/tenants/{id}/regenerate-credentials")
    suspend fun regenerateCredentials(@PathVariable id: UUID): ResponseEntity<Any> {
        val creds = tenantConfigService.regenerateCredentials(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(ErrorDetail("not_found", "Tenant not found")))
        return ResponseEntity.ok(creds)
    }

    // ── User Management ───────────────────────────────────

    @PostMapping("/users")
    suspend fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<Any> {
        return try {
            val role = UserRole.valueOf(request.role.uppercase())
            val user = userService.createUser(request.email, request.name, request.password, role, request.tenantId)
            ResponseEntity.status(HttpStatus.CREATED).body(userService.toResponse(user))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ErrorResponse(ErrorDetail("validation_error", e.message ?: "Invalid input")))
        }
    }

    @GetMapping("/users")
    suspend fun listUsers(@RequestParam("tenant_id", required = false) tenantId: UUID?): ResponseEntity<List<UserResponse>> {
        return ResponseEntity.ok(userService.listUsers(tenantId).map { userService.toResponse(it) })
    }

    @GetMapping("/users/{id}")
    suspend fun getUser(@PathVariable id: UUID): ResponseEntity<Any> {
        val user = userService.getUser(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(ErrorDetail("not_found", "User not found")))
        return ResponseEntity.ok(userService.toResponse(user))
    }

    @PutMapping("/users/{id}")
    suspend fun updateUser(@PathVariable id: UUID, @RequestBody request: UpdateUserRequest): ResponseEntity<Any> {
        val role = request.role?.let { UserRole.valueOf(it.uppercase()) }
        val user = userService.updateUser(id, request.name, role, request.active)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(ErrorDetail("not_found", "User not found")))
        return ResponseEntity.ok(userService.toResponse(user))
    }

    @DeleteMapping("/users/{id}")
    suspend fun deleteUser(@PathVariable id: UUID): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }

    // ── Dashboard Stats ───────────────────────────────────

    @GetMapping("/dashboard/stats")
    suspend fun dashboardStats(@RequestParam("tenant_id", required = false) tenantId: UUID?): ResponseEntity<DashboardStatsResponse> {
        // In production: query from PostgreSQL + Prometheus
        // For now: return realistic demo data
        return ResponseEntity.ok(DashboardStatsResponse(
            totalDecisionsToday = 12_847,
            totalDecisions7d = 89_234,
            avgLatencyMs = 87.0,
            p99LatencyMs = 164.0,
            offerAttachRate = 0.34,
            degradedRate = 0.02,
            activeTenants = tenantConfigService.listTenants().count { it.status == "ACTIVE" },
            totalUsers = userService.listUsers().size,
            decisionsByChannel = mapOf(
                "agentforce" to 5_421,
                "genesys" to 3_892,
                "app" to 2_834,
                "ivr" to 700
            ),
            decisionsByChurnBand = mapOf(
                "LOW" to 4_231,
                "MEDIUM" to 5_012,
                "HIGH" to 2_891,
                "CRITICAL" to 713
            ),
            topOffers = listOf(
                TopOfferDto("VAS-STREAM-PLUS", "StreamPlus 3-month free trial", 4_521, 1_537, 0.34),
                TopOfferDto("VAS-DATA-BOOST", "Double Data 6 months", 3_892, 1_168, 0.30),
                TopOfferDto("VAS-ROAM-FREE", "Free GCC Roaming 30 days", 2_134, 747, 0.35),
                TopOfferDto("VAS-LOYALTY-GOLD", "Gold Loyalty Discount 20%", 1_823, 674, 0.37),
                TopOfferDto("VAS-FAMILY-PLAN", "Family Plan Add 3 Lines", 1_234, 383, 0.31)
            )
        ))
    }
}
