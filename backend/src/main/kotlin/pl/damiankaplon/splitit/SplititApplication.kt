package pl.damiankaplon.splitit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

/**
 * A modular monolith (Spring Modulith): every direct subpackage is an
 * application module, whose subpackages are its internals. Modules talk
 * to each other through events, see `ModularityTest`.
 */
@SpringBootApplication
@EnableAsync
class SplititApplication

fun main(args: Array<String>) {
    runApplication<SplititApplication>(*args)
}
