package pl.damiankaplon.splitit

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * The application modules (`balancing`, `kernel`, `personal`, `project`)
 * depend on each other without cycles and only through each other's
 * top-level package; their subpackages are internal.
 */
class ModularityTest {
    private val modules = ApplicationModules.of(SplititApplication::class.java)

    @Test
    fun `modules depend on each other only through their API and without cycles`() {
        modules.verify()
    }

    /** Diagrams of the modules, written to `build/spring-modulith-docs`. */
    @Test
    fun `documents the modules`() {
        Documenter(modules).writeDocumentation()
    }
}
