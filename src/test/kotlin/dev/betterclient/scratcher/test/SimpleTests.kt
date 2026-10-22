package dev.betterclient.scratcher.test

import dev.betterclient.scratcher.compileAndRun
import kotlin.random.Random
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
                    say("Hello ${name}");
                }
            """.trimIndent()
        ) {
            assertAsk("What is your name?", "test")
            assertSay("Hello test")
        }
    }

    @Test
    fun math() {
        compileAndRun(
            $$"""
                import looks::say;
                import sensing::ask;
                import cast;
                
                on GreenFlag {
                    say("${1 + 1}");
                    say("${ cast::toInt(ask("Number 0")) * 5 }");
                }
            """.trimIndent()
        ) {
            assertSay("2")
            assertAsk("Number 0", "5")
            assertSay("25")
        }
    }

    @Test
    fun `if else`() {
        compileAndRun(
            """
            import looks::say;
            import sensing::ask;
            import cast;
            
            on GreenFlag {
                auto score = cast::toInt(ask("number?"));
                if (score > 5) {
                    say("Pass");
                } else {
                    say("Fail");
                }
            }
        """.trimIndent()
        ) {
            val out = Random.nextInt()
            assertAsk("number?", out.toString())
            assertSay(if (out > 5) "Pass" else "Fail")
        }
    }

    @Test
    fun loops() {
        compileAndRun(
            $$"""
            import looks::say;
            import sensing::ask;
            import cast;
            
            on GreenFlag {
                auto score = cast::toInt(ask("number?"));
                int a = 0;
                while(a < score) {
                    a++;
                }
                say("${a}");
            }
        """.trimIndent()
        ) {
            val out = Random.nextInt(5000)
            assertAsk("number?", out.toString())
            assertSay(out.toString())
        }
    }

    @Test
    fun `manual gc collect`() {
        compileAndRun(
            """
                import gc;
                
                on GreenFlag {
                    gc::collect();
                }
            """.trimIndent()
        )
    }

    @Test
    fun `simple struct`() {
        compileAndRun($$"""
            import sensing::ask;
            import looks::say;
            
            struct TestStruct0(str name);
            
            on GreenFlag {
                auto name = a();
                say("Your name is ${name.name}");
            }
            
            warp TestStruct0 a() {
                return TestStruct0(ask("What's your name?"));
            }
        """.trimIndent()) {
            assertAsk("What's your name?", "test")
            assertSay("Your name is test")
        }
    }

    @Test
    fun `simple array`() {
        compileAndRun($$"""
            import sensing::ask;
            import array::*;
            import looks::say;
            import cast;
            
            on GreenFlag {
                str[] myArr = arrayOf(cast::toInt(ask("Size?")), (int index) -> { "Index: ${index}" });
            
                for(str a in myArr) {
                    say(a);
                }
            }
        """.trimIndent()) {
            assertAsk("Size?", "15")
            repeat(15) { index ->
                assertSay("Index: $index")
            }
        }
    }

    @Test
    fun `simple list`() {
        compileAndRun($$"""
            import sensing::ask;
            import list::*;
            import array::*;
            import looks::say;
            import cast;
            
            on GreenFlag {
                List<str> myArr = arrayOf(cast::toInt(ask("Size?")), (int index) -> { "Index: ${index}" }).toList();
            
                for(str a in myArr) {
                    say(a);
                }
            }
        """.trimIndent()) {
            assertAsk("Size?", "15")
            repeat(15) { index ->
                assertSay("Index: $index")
            }
        }
    }
}