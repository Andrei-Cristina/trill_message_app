package data.models

import io.ktor.server.auth.*

data class UserPrincipal( val userEmail: String): Principal