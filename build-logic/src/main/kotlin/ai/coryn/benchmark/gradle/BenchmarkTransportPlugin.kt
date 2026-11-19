package ai.coryn.benchmark.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter

class BenchmarkTransportPlugin : Plugin<Project> {
    override fun apply(target: Project) = BenchmarkTransportPluginImpl(target).apply()
}

internal class BenchmarkTransportPluginImpl(private val project: Project) {
    fun apply() {
        project.pluginManager.withPlugin("com.android.application") {
            project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java).onVariants { variant ->
                if (variant.productFlavors.any { it.second == "plain" }) return@onVariants
                variant.instrumentation.transformClassesWith(BenchmarkTransportVisitorFactory::class.java, InstrumentationScope.ALL) { }
                variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
            }
        }
    }
}

abstract class BenchmarkTransportVisitorFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    private val impl: BenchmarkTransportVisitorFactoryImpl get() = BenchmarkTransportVisitorFactoryImpl()
    override fun isInstrumentable(classData: ClassData): Boolean = impl.isInstrumentable(classData)
    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        impl.create(nextClassVisitor, instrumentationContext.apiVersion.get())
}

internal class BenchmarkTransportVisitorFactoryImpl {
    fun isInstrumentable(classData: ClassData): Boolean = classData.className.startsWith("ai.coryn.replay.internal.core.")
    fun create(next: ClassVisitor, api: Int): ClassVisitor = BenchmarkClientVisitor(api, next)
}

internal class BenchmarkClientVisitor(api: Int, next: ClassVisitor) : ClassVisitor(api, next) {
    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        val next = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
        if (!descriptor.startsWith("(Landroid/app/Application;") || !descriptor.endsWith(")Lokhttp3/OkHttpClient;")) {
            return next
        }
        return BenchmarkClientMethodVisitor(api, next, access, name, descriptor)
    }
}

internal class BenchmarkClientMethodVisitor(api: Int, next: MethodVisitor, access: Int, name: String, descriptor: String) :
    AdviceAdapter(api, next, access, name, descriptor) {
    private var applicationLocal = -1

    override fun onMethodEnter() {
        applicationLocal = newLocal(Type.getType("Landroid/app/Application;"))
        loadArg(0)
        storeLocal(applicationLocal)
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (opcode == Opcodes.INVOKEVIRTUAL && owner == "okhttp3/OkHttpClient\$Builder" && name == "build") {
            loadLocal(applicationLocal)
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "ai/coryn/replay/benchmark/BenchmarkTransport", "configure",
                "(Lokhttp3/OkHttpClient\$Builder;Landroid/app/Application;)Lokhttp3/OkHttpClient\$Builder;", false)
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
}
