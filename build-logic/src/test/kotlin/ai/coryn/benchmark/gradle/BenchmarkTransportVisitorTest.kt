package ai.coryn.benchmark.gradle

import org.junit.Assert.assertEquals
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal class BenchmarkTransportVisitorTest {
    @Test fun publishedStaticFactoryPassesItsApplicationArgument() = checkFactory(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, 0)
    @Test fun sourceCompanionFactoryPassesItsApplicationArgument() = checkFactory(Opcodes.ACC_PUBLIC, 1)

    private fun checkFactory(access: Int, expectedSlot: Int) {
        val writer = ClassWriter(0)
        val visitor = BenchmarkClientVisitor(Opcodes.ASM9, writer)
        visitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "SdkFactory", null, "java/lang/Object", null)
        val method = visitor.visitMethod(access, "obfuscatedName", "(Landroid/app/Application;)Lokhttp3/OkHttpClient;", null, null)!!
        method.visitCode()
        method.visitInsn(Opcodes.ICONST_0)
        method.visitVarInsn(Opcodes.ISTORE, expectedSlot)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "okhttp3/OkHttpClient\$Builder", "build", "()Lokhttp3/OkHttpClient;", false)
        method.visitInsn(Opcodes.ARETURN)
        method.visitMaxs(2, 2)
        method.visitEnd()
        visitor.visitEnd()
        val loads = mutableListOf<Int>()
        val calls = mutableListOf<String>()
        ClassReader(writer.toByteArray()).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String, d: String, s: String?, e: Array<out String>?): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                override fun visitVarInsn(opcode: Int, variable: Int) { if (opcode == Opcodes.ALOAD) loads.add(variable) }
                override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    calls.add(owner + "." + name)
                }
            }
        }, 0)
        assertEquals(listOf(expectedSlot, expectedSlot + 1), loads)
        assertEquals(listOf("ai/coryn/replay/benchmark/BenchmarkTransport.configure", "okhttp3/OkHttpClient\$Builder.build"), calls)
    }
}
