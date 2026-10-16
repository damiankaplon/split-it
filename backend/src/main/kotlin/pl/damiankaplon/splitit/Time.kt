package pl.damiankaplon.splitit

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
class Time {

    fun now(): Instant = Instant.now()

    fun zone(): ZoneId = ZoneId.of("Europe/Warsaw")
}