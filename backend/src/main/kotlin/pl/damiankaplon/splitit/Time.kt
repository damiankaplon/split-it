package pl.damiankaplon.splitit

import org.springframework.stereotype.Component
import java.time.Instant

@Component
class Time {

    fun now(): Instant = Instant.now()
}