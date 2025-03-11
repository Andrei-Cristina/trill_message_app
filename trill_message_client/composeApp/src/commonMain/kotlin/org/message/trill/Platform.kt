package org.message.trill

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform