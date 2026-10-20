package dev.betterclient.scratcher.test

import dev.betterclient.scratcher.compileAndRun
import kotlin.test.Test

class SimpleTests {
    @Test
    fun `hello world`() {
        compileAndRun(
            """
                import looks::say;
                
                on GreenFlag {
                    say("Hello world!");
                }
            """.trimIndent()
        ) {
            assertSay("Hello world!")
        }
    }

    @Test
    fun `ask and answer`() {
        compileAndRun(
            $$"""
                import looks::say;
                import sensing::ask;
                
                on GreenFlag {
                    auto name = ask("What is your name?");
                    say("Hello $name");
                }
            """.trimIndent()
        ) {
            assertAsk("What is your name?", "test")
            assertSay("Hello test")
        }
    }
}