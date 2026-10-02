package pl.damiankaplon.splitit

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<SplititApplication>().with(PostgreTestContainerConfig::class).run(*args)
}
