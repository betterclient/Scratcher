package dev.betterclient.scratcher.test

import dev.betterclient.scratcher.compileAndRun
import kotlin.random.Random
import kotlin.test.Test

class CompactLists {
    @Test
    fun `compact int list`() {
        compileAndRun($$"""
            import sensing::ask;
            import looks::say;
            import cast::*;
            import compact_int_list::*;
            
            on GreenFlag {
                IntList myList = newIntList();
                
                while(true) {
                    when(ask("What to do?")) {
                        "add" -> {
                            myList.add(toInt(ask("Number?")));
                        }
                        "deleteAtIndex" -> {
                            myList.removeAt(toInt(ask("Index?")));
                        }
                        "print" -> {
                            myList.forEach((int i) -> say("${i}") );
                        }
                        "exit" -> break;
                        else -> break;
                    }
                }
            }
        """.trimIndent()) {
            val shadowList = mutableListOf<Int>()
            val steps = Random.nextInt(5, 10)

            repeat(steps) {
                val action = if (shadowList.isEmpty()) {
                    "add"
                } else {
                    listOf("add", "deleteAtIndex", "print").random()
                }

                assertAsk("What to do?", action)

                when (action) {
                    "add" -> {
                        val num = Random.nextInt(-1000, 1000)
                        assertAsk("Number?", num.toString())
                        shadowList.add(num)
                    }
                    "deleteAtIndex" -> {
                        val index = Random.nextInt(0, shadowList.size)
                        assertAsk("Index?", index.toString())
                        shadowList.removeAt(index)
                    }
                    "print" -> {
                        shadowList.forEach { num ->
                            assertSay(num.toString())
                        }
                    }
                }
            }

            assertAsk("What to do?", "print")
            shadowList.forEach { num ->
                assertSay(num.toString())
            }

            assertAsk("What to do?", "exit")
        }
    }

    @Test
    fun `compact float list`() {
        compileAndRun($$"""
            import sensing::ask;
            import looks::say;
            import cast::*;
            import compact_float_list::*;
            
            on GreenFlag {
                FloatList myList = newFloatList();
                
                while(true) {
                    when(ask("What to do?")) {
                        "add" -> {
                            myList.add(toFloat(ask("Number?")));
                        }
                        "deleteAtIndex" -> {
                            myList.removeAt(toInt(ask("Index?")));
                        }
                        "print" -> {
                            myList.forEach((float i) -> say("${i}") );
                        }
                        "exit" -> break;
                        else -> break;
                    }
                }
            }
        """.trimIndent()) {
            val shadowList = mutableListOf<Float>()
            val steps = Random.nextInt(5, 10)

            repeat(steps) {
                val action = if (shadowList.isEmpty()) {
                    "add"
                } else {
                    listOf("add", "deleteAtIndex", "print").random()
                }

                assertAsk("What to do?", action)

                when (action) {
                    "add" -> {
                        val num = Random.nextDouble(-1000.0, 1000.0).toFloat()
                        assertAsk("Number?", num.toString())
                        shadowList.add(num)
                    }
                    "deleteAtIndex" -> {
                        val index = Random.nextInt(0, shadowList.size)
                        assertAsk("Index?", index.toString())
                        shadowList.removeAt(index)
                    }
                    "print" -> {
                        shadowList.forEach { num ->
                            assertSay(num.toString())
                        }
                    }
                }
            }

            assertAsk("What to do?", "print")
            shadowList.forEach { num ->
                assertSay(num.toString())
            }

            assertAsk("What to do?", "exit")
        }
    }
}