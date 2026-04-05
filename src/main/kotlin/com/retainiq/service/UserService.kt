package com.retainiq.service

import com.retainiq.domain.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class UserService(private val tenantService: TenantService) {

    private val users = ConcurrentHashMap<UUID, User>()

    init {
        // Seed a super admin
        val adminId = UUID.fromString("00000000-0000-0000-0000-000000000099")
        users[adminId] = User(
            id = adminId,
            email = "admin@retainiq.com",
            name = "Platform Admin",
            passwordHash = hashPassword("admin123"),
            role = UserRole.SUPER_ADMIN,
            tenantId = null,
            active = true,
            lastLoginAt = null,
            createdAt = Instant.now()
        )

        // Seed a demo tenant admin
        val demoAdminId = UUID.fromString("00000000-0000-0000-0000-000000000098")
        users[demoAdminId] = User(
            id = demoAdminId,
            email = "admin@demo-operator.com",
            name = "Demo Operator Admin",
            passwordHash = hashPassword("demo123"),
            role = UserRole.TENANT_ADMIN,
            tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            active = true,
            lastLoginAt = null,
            createdAt = Instant.now()
        )
        logger.info { "Seeded ${users.size} users" }
    }

    fun authenticate(email: String, password: String): User? {
        val user = users.values.find { it.email == email && it.active }
        if (user != null && user.passwordHash == hashPassword(password)) {
            users[user.id] = user.copy(lastLoginAt = Instant.now())
            return users[user.id]
        }
        return null
    }

    fun createUser(email: String, name: String, password: String, role: UserRole, tenantId: UUID?): User {
        if (users.values.any { it.email == email }) {
            throw IllegalArgumentException("User with email $email already exists")
        }
        val user = User(
            id = UUID.randomUUID(),
            email = email,
            name = name,
            passwordHash = hashPassword(password),
            role = role,
            tenantId = tenantId,
            active = true,
            lastLoginAt = null,
            createdAt = Instant.now()
        )
        users[user.id] = user
        logger.info { "Created user ${user.email} role=${user.role}" }
        return user
    }

    fun getUser(id: UUID): User? = users[id]

    fun listUsers(tenantId: UUID? = null): List<User> {
        return if (tenantId != null) {
            users.values.filter { it.tenantId == tenantId }.sortedByDescending { it.createdAt }
        } else {
            users.values.sortedByDescending { it.createdAt }
        }
    }

    fun updateUser(id: UUID, name: String?, role: UserRole?, active: Boolean?): User? {
        val user = users[id] ?: return null
        val updated = user.copy(
            name = name ?: user.name,
            role = role ?: user.role,
            active = active ?: user.active
        )
        users[id] = updated
        return updated
    }

    fun deleteUser(id: UUID): Boolean {
        return users.remove(id) != null
    }

    fun toResponse(user: User): com.retainiq.api.dto.UserResponse {
        val tenantName = user.tenantId?.let { tenantService.getTenant(it)?.name }
        return com.retainiq.api.dto.UserResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            role = user.role.name,
            tenantId = user.tenantId,
            tenantName = tenantName,
            active = user.active,
            lastLoginAt = user.lastLoginAt?.toString(),
            createdAt = user.createdAt.toString()
        )
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
