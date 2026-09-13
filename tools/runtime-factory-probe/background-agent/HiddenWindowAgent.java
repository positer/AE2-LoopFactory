import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Test launcher only: create real OpenGL windows without ever displaying or focusing them. */
public final class HiddenWindowAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (!name.equals("org/lwjgl/glfw/GLFW")) return null;
                var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        var next = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if (method.equals("glfwShowWindow") || method.equals("glfwFocusWindow")) {
                            return new MethodVisitor(Opcodes.ASM9, next) {
                                @Override public void visitCode() { super.visitCode(); visitInsn(Opcodes.RETURN); }
                            };
                        }
                        if (method.equals("glfwCreateWindow")) return new MethodVisitor(Opcodes.ASM9, next) {
                            @Override public void visitCode() {
                                super.visitCode();
                                for (int hint : new int[]{0x00020004, 0x00020001}) {
                                    visitLdcInsn(hint); visitInsn(Opcodes.ICONST_0);
                                    visitMethodInsn(Opcodes.INVOKESTATIC, name, "glfwWindowHint", "(II)V", false);
                                }
                            }
                        };
                        return next;
                    }
                }, 0);
                System.err.println("AE2LF background agent: GLFW creation forced invisible; show/focus disabled");
                return writer.toByteArray();
            }
        });
    }
}
