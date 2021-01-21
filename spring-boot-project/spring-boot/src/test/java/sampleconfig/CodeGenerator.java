package sampleconfig;

import sun.security.action.GetBooleanAction;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

public class CodeGenerator {

	private static final boolean saveGeneratedFiles = AccessController
			.doPrivileged(new GetBooleanAction("sun.misc.CodeGenerator.saveGeneratedFiles"));

	private static final Method hashCodeMethod;

	private static final Method equalsMethod;

	private static final Method toStringMethod;

	static {
		try {
			hashCodeMethod = Object.class.getMethod("hashCode");
			equalsMethod = Object.class.getMethod("equals", Object.class);
			toStringMethod = Object.class.getMethod("toString");
		}
		catch (NoSuchMethodException var1) {
			throw new NoSuchMethodError(var1.getMessage());
		}
	}

	private final String className;

	private final Class<?>[] interfaces;

	private final int accessFlags;

	private final CodeGenerator.ConstantPool cp = new CodeGenerator.ConstantPool();

	private final List<CodeGenerator.FieldInfo> fields = new ArrayList<>();

	private final List<CodeGenerator.MethodInfo> methods = new ArrayList<>();

	private final Map<String, List<CodeGenerator.ProxyMethod>> proxyMethods = new HashMap<>();

	private int proxyMethodCount = 0;

	private CodeGenerator(String className, Class<?>[] interfaces, int accessFlags) {
		this.className = className;
		this.interfaces = interfaces;
		this.accessFlags = accessFlags;
	}

	public static byte[] generateClass(final String className, Class<?>[] interfaces) {
		CodeGenerator codeGenerator = new CodeGenerator(className, interfaces, 49);
		return codeGenerator.generateClassFile();
	}

	public static byte[] generateProxyClass(final String className, Class<?>[] interfaces) {
		return generateProxyClass(className, interfaces, 49);
	}

	public static byte[] generateProxyClass(final String className, Class<?>[] interfaces, int accessFlags) {
		CodeGenerator codeGenerator = new CodeGenerator(className, interfaces, accessFlags);
		final byte[] classBytes = codeGenerator.generateClassFile();
		if (saveGeneratedFiles) {
			// 保存类
			AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
				try {
					int last = className.lastIndexOf(46);
					Path filePath;
					if (last > 0) {
						Path fileDir = Paths.get(className.substring(0, last).replace('.', File.separatorChar));
						Files.createDirectories(fileDir);
						filePath = fileDir.resolve(className.substring(last + 1) + ".class");
					}
					else {
						filePath = Paths.get(className + ".class");
					}
					Files.write(filePath, classBytes);
					return null;
				}
				catch (IOException e) {
					throw new InternalError("I/O exception saving generated file: " + e);
				}
			});
		}
		return classBytes;
	}

	private static void checkReturnTypes(List<CodeGenerator.ProxyMethod> var0) {
		if (var0.size() >= 2) {
			LinkedList<Class<?>> var1 = new LinkedList<>();
			Iterator<ProxyMethod> var2 = var0.iterator();
			boolean var5;
			label49: do {
				while (var2.hasNext()) {
					CodeGenerator.ProxyMethod var3 = var2.next();
					Class<?> var4 = var3.returnType;
					if (var4.isPrimitive()) {
						throw new IllegalArgumentException("methods with same signature "
								+ getFriendlyMethodSignature(var3.methodName, var3.parameterTypes)
								+ " but incompatible return types: " + var4.getName() + " and others");
					}
					var5 = false;
					ListIterator<Class<?>> var6 = var1.listIterator();
					while (var6.hasNext()) {
						Class<?> var7 = var6.next();
						if (var4.isAssignableFrom(var7)) {
							continue label49;
						}
						if (var7.isAssignableFrom(var4)) {
							if (!var5) {
								var6.set(var4);
								var5 = true;
							}
							else {
								var6.remove();
							}
						}
					}
					if (!var5) {
						var1.add(var4);
					}
				}
				if (var1.size() > 1) {
					CodeGenerator.ProxyMethod var8 = var0.get(0);
					throw new IllegalArgumentException("methods with same signature "
							+ getFriendlyMethodSignature(var8.methodName, var8.parameterTypes)
							+ " but incompatible return types: " + var1);
				}
				return;
				// } while ($assertionsDisabled || !var5);
			}
			while (!var5);
			throw new AssertionError();
		}
	}

	private static String dotToSlash(String var0) {
		return var0.replace('.', '/');
	}

	private static String getMethodDescriptor(Class<?>[] var0, Class<?> var1) {
		return getParameterDescriptors(var0) + (var1 == Void.TYPE ? "V" : getFieldType(var1));
	}

	private static String getParameterDescriptors(Class<?>[] var0) {
		StringBuilder var1 = new StringBuilder("(");
		for (Class<?> aClass : var0) {
			var1.append(getFieldType(aClass));
		}
		var1.append(')');
		return var1.toString();
	}

	private static String getFieldType(Class<?> var0) {
		if (var0.isPrimitive()) {
			return CodeGenerator.PrimitiveTypeInfo.get(var0).baseTypeString;
		}
		else {
			return var0.isArray() ? var0.getName().replace('.', '/') : "L" + dotToSlash(var0.getName()) + ";";
		}
	}

	private static String getFriendlyMethodSignature(String var0, Class<?>[] classes) {
		StringBuilder stringBuilder = new StringBuilder(var0);
		stringBuilder.append('(');
		for (int len = 0; len < classes.length; ++len) {
			if (len > 0) {
				stringBuilder.append(',');
			}
			Class<?> aClass = classes[len];
			int var5;
			for (var5 = 0; aClass.isArray(); ++var5) {
				aClass = aClass.getComponentType();
			}
			stringBuilder.append(aClass.getName());
			while (var5-- > 0) {
				stringBuilder.append("[]");
			}
		}
		stringBuilder.append(')');
		return stringBuilder.toString();
	}

	private static int getWordsPerType(Class<?> var0) {
		return var0 != Long.TYPE && var0 != Double.TYPE ? 1 : 2;
	}

	private static void collectCompatibleTypes(Class<?>[] var0, Class<?>[] var1, List<Class<?>> var2) {
		for (Class<?> var6 : var0) {
			if (var2.contains(var6)) {
				continue;
			}
			for (Class<?> var10 : var1) {
				if (var10.isAssignableFrom(var6)) {
					var2.add(var6);
					break;
				}
			}
		}
	}

	private static List<Class<?>> computeUniqueCatchList(Class<?>[] classes) {
		List<Class<?>> var1 = new ArrayList<>();
		var1.add(Error.class);
		var1.add(RuntimeException.class);
		label36: for (Class<?> aClass : classes) {
			if (aClass.isAssignableFrom(Throwable.class)) {
				var1.clear();
				break;
			}
			if (!Throwable.class.isAssignableFrom(aClass)) {
				continue;
			}
			int var6 = 0;
			while (var6 < var1.size()) {
				Class<?> var7 = var1.get(var6);
				if (var7.isAssignableFrom(aClass)) {
					continue label36;
				}
				if (aClass.isAssignableFrom(var7)) {
					var1.remove(var6);
				}
				else {
					++var6;
				}
			}
			var1.add(aClass);
		}
		return var1;
	}

	private byte[] generateClassFile() {
		this.addProxyMethod(hashCodeMethod, Object.class);
		this.addProxyMethod(equalsMethod, Object.class);
		this.addProxyMethod(toStringMethod, Object.class);

		for (Class<?> anInterface : interfaces) {
			Method[] methods = anInterface.getMethods();
			for (Method method : methods) {
				this.addProxyMethod(method, anInterface);
			}
		}
		for (List<ProxyMethod> value : proxyMethods.values()) {
			checkReturnTypes(value);
		}

		try {
			this.methods.add(this.generateConstructor());
			for (List<ProxyMethod> value : proxyMethods.values()) {
				for (ProxyMethod proxyMethod : value) {
					this.fields.add(
							new CodeGenerator.FieldInfo(proxyMethod.methodFieldName, "Ljava/lang/reflect/Method;", 10));
					this.methods.add(proxyMethod.generateMethod());
				}
			}
			this.methods.add(this.generateStaticInitializer());
		}
		catch (IOException var10) {
			throw new InternalError("unexpected I/O Exception", var10);
		}

		if (this.methods.size() > 65535) {
			throw new IllegalArgumentException("method limit exceeded");
		}
		else if (this.fields.size() > 65535) {
			throw new IllegalArgumentException("field limit exceeded");
		}
		else {
			this.cp.getClass(dotToSlash(this.className));
			this.cp.getClass("java/lang/reflect/Proxy");
			for (Class<?> anInterface : interfaces) {
				this.cp.getClass(dotToSlash(anInterface.getName()));
			}
			this.cp.setReadOnly();
			ByteArrayOutputStream classBytes = new ByteArrayOutputStream();
			DataOutputStream outputStream = new DataOutputStream(classBytes);

			try {
				// 1.写入魔数
				outputStream.writeInt(-889275714);
				// 2.写入次版本号
				outputStream.writeShort(0);
				// 3.写入主版本号
				outputStream.writeShort(49);
				// 4.写入常量池
				this.cp.write(outputStream);
				// 5.写入访问修饰符
				outputStream.writeShort(this.accessFlags);
				// 6.写入类索引
				outputStream.writeShort(this.cp.getClass(dotToSlash(this.className)));
				// 7.写入父类索引, 生成的代理类都继承自Proxy
				outputStream.writeShort(this.cp.getClass("java/lang/reflect/Proxy"));
				// 8.写入接口计数值
				outputStream.writeShort(this.interfaces.length);
				// 9.写入接口集合
				for (Class<?> anInterface : this.interfaces) {
					outputStream.writeShort(this.cp.getClass(dotToSlash(anInterface.getName())));
				}
				// 10.写入字段计数值
				outputStream.writeShort(this.fields.size());
				// 11.写入字段集合
				for (FieldInfo field : fields) {
					field.write(outputStream);
				}
				// 12.写入方法计数值
				outputStream.writeShort(this.methods.size());
				// 13.写入方法集合
				for (MethodInfo method : methods) {
					method.write(outputStream);
				}
				// 14.写入属性计数值, 代理类class文件没有属性所以为0
				outputStream.writeShort(0);
				return classBytes.toByteArray();
			}
			catch (IOException var9) {
				throw new InternalError("unexpected I/O Exception", var9);
			}
		}
	}

	private void addProxyMethod(Method method, Class<?> target) {
		String methodName = method.getName();
		Class<?>[] parameterTypes = method.getParameterTypes();
		Class<?> returnType = method.getReturnType();
		Class<?>[] exceptionTypes = method.getExceptionTypes();
		String var7 = methodName + getParameterDescriptors(parameterTypes);
		List<CodeGenerator.ProxyMethod> proxyMethods = this.proxyMethods.get(var7);
		if (proxyMethods != null) {
			for (CodeGenerator.ProxyMethod proxyMethod : proxyMethods) {
				if (returnType == proxyMethod.returnType) {
					List<Class<?>> var11 = new ArrayList<>();
					collectCompatibleTypes(exceptionTypes, proxyMethod.exceptionTypes, var11);
					collectCompatibleTypes(proxyMethod.exceptionTypes, exceptionTypes, var11);
					proxyMethod.exceptionTypes = new Class[var11.size()];
					proxyMethod.exceptionTypes = var11.toArray(proxyMethod.exceptionTypes);
					return;
				}
			}
		}
		else {
			proxyMethods = new ArrayList<>(3);
			this.proxyMethods.put(var7, proxyMethods);
		}
		proxyMethods.add(new CodeGenerator.ProxyMethod(methodName, parameterTypes, returnType, exceptionTypes, target));
	}

	private CodeGenerator.MethodInfo generateConstructor() throws IOException {
		CodeGenerator.MethodInfo methodInfo = new CodeGenerator.MethodInfo("<init>",
				"(Ljava/lang/reflect/InvocationHandler;)V", 1);
		DataOutputStream outputStream = new DataOutputStream(methodInfo.code);
		this.code_aload(0, outputStream);
		this.code_aload(1, outputStream);
		outputStream.writeByte(183);
		outputStream.writeShort(
				this.cp.getMethodRef("java/lang/reflect/Proxy", "<init>", "(Ljava/lang/reflect/InvocationHandler;)V"));
		outputStream.writeByte(177);
		methodInfo.maxStack = 10;
		methodInfo.maxLocals = 2;
		methodInfo.declaredExceptions = new short[0];
		return methodInfo;
	}

	private CodeGenerator.MethodInfo generateStaticInitializer() throws IOException {
		CodeGenerator.MethodInfo var1 = new CodeGenerator.MethodInfo("<clinit>", "()V", 8);
		byte var2 = 1;
		byte var4 = 0;
		DataOutputStream outputStream = new DataOutputStream(var1.code);
		for (List<ProxyMethod> proxyMethodList : this.proxyMethods.values()) {
			for (Object o : proxyMethodList) {
				ProxyMethod var10 = (ProxyMethod) o;
				var10.codeFieldInitialization(outputStream);
			}
		}

		outputStream.writeByte(177);
		short var3;
		short var5 = var3 = (short) var1.code.size();
		var1.exceptionTable.add(new CodeGenerator.ExceptionTableEntry(var4, var5, var3,
				this.cp.getClass("java/lang/NoSuchMethodException")));
		this.code_astore(var2, outputStream);
		outputStream.writeByte(187);
		outputStream.writeShort(this.cp.getClass("java/lang/NoSuchMethodError"));
		outputStream.writeByte(89);
		this.code_aload(var2, outputStream);
		outputStream.writeByte(182);
		outputStream.writeShort(this.cp.getMethodRef("java/lang/Throwable", "getMessage", "()Ljava/lang/String;"));
		outputStream.writeByte(183);
		outputStream.writeShort(this.cp.getMethodRef("java/lang/NoSuchMethodError", "<init>", "(Ljava/lang/String;)V"));
		outputStream.writeByte(191);
		var3 = (short) var1.code.size();
		var1.exceptionTable.add(new CodeGenerator.ExceptionTableEntry(var4, var5, var3,
				this.cp.getClass("java/lang/ClassNotFoundException")));
		this.code_astore(var2, outputStream);
		outputStream.writeByte(187);
		outputStream.writeShort(this.cp.getClass("java/lang/NoClassDefFoundError"));
		outputStream.writeByte(89);
		this.code_aload(var2, outputStream);
		outputStream.writeByte(182);
		outputStream.writeShort(this.cp.getMethodRef("java/lang/Throwable", "getMessage", "()Ljava/lang/String;"));
		outputStream.writeByte(183);
		outputStream
				.writeShort(this.cp.getMethodRef("java/lang/NoClassDefFoundError", "<init>", "(Ljava/lang/String;)V"));
		outputStream.writeByte(191);
		if (var1.code.size() > 65535) {
			throw new IllegalArgumentException("code size limit exceeded");
		}
		else {
			var1.maxStack = 10;
			var1.maxLocals = (short) (var2 + 1);
			var1.declaredExceptions = new short[0];
			return var1;
		}
	}

	private void code_iload(int var1, DataOutputStream outputStream) throws IOException {
		this.codeLocalLoadStore(var1, 21, 26, outputStream);
	}

	private void code_lload(int var1, DataOutputStream outputStream) throws IOException {
		this.codeLocalLoadStore(var1, 22, 30, outputStream);
	}

	private void code_fload(int var1, DataOutputStream outputStream) throws IOException {
		this.codeLocalLoadStore(var1, 23, 34, outputStream);
	}

	private void code_dload(int var1, DataOutputStream outputStream) throws IOException {
		this.codeLocalLoadStore(var1, 24, 38, outputStream);
	}

	private void code_aload(int var1, DataOutputStream outputStream) throws IOException {
		this.codeLocalLoadStore(var1, 25, 42, outputStream);
	}

	private void code_astore(int var1, DataOutputStream outputStream) throws IOException {
		this.codeLocalLoadStore(var1, 58, 75, outputStream);
	}

	private void codeLocalLoadStore(int var1, int var2, int var3, DataOutputStream outputStream) throws IOException {
		assert var1 >= 0 && var1 <= 65535;
		if (var1 <= 3) {
			outputStream.writeByte(var3 + var1);
		}
		else if (var1 <= 255) {
			outputStream.writeByte(var2);
			outputStream.writeByte(var1 & 255);
		}
		else {
			outputStream.writeByte(196);
			outputStream.writeByte(var2);
			outputStream.writeShort(var1 & '\uffff');
		}
	}

	private void code_ldc(int var1, DataOutputStream outputStream) throws IOException {
		assert var1 >= 0 && var1 <= 65535;
		if (var1 <= 255) {
			outputStream.writeByte(18);
			outputStream.writeByte(var1 & 255);
		}
		else {
			outputStream.writeByte(19);
			outputStream.writeShort(var1 & '\uffff');
		}
	}

	private void code_ipush(int var1, DataOutputStream outputStream) throws IOException {
		if (var1 >= -1 && var1 <= 5) {
			outputStream.writeByte(3 + var1);
		}
		else if (var1 >= -128 && var1 <= 127) {
			outputStream.writeByte(16);
			outputStream.writeByte(var1 & 255);
		}
		else {
			if (var1 < -32768 || var1 > 32767) {
				throw new AssertionError();
			}
			outputStream.writeByte(17);
			outputStream.writeShort(var1 & '\uffff');
		}
	}

	private void codeClassForName(Class<?> var1, DataOutputStream var2) throws IOException {
		this.code_ldc(this.cp.getString(var1.getName()), var2);
		var2.writeByte(184);
		var2.writeShort(this.cp.getMethodRef("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;"));
	}

	private static class ConstantPool {

		private final List<CodeGenerator.ConstantPool.Entry> pool;

		private final Map<Object, Short> map;

		private boolean readOnly;

		private ConstantPool() {
			this.pool = new ArrayList<>(32);
			this.map = new HashMap<>(16);
			this.readOnly = false;
		}

		public short getUtf8(String var1) {
			if (var1 == null) {
				throw new NullPointerException();
			}
			else {
				return this.getValue(var1);
			}
		}

		public short getClass(String var1) {
			short var2 = this.getUtf8(var1);
			return this.getIndirect(new CodeGenerator.ConstantPool.IndirectEntry(7, var2));
		}

		public short getString(String var1) {
			short var2 = this.getUtf8(var1);
			return this.getIndirect(new CodeGenerator.ConstantPool.IndirectEntry(8, var2));
		}

		public short getFieldRef(String var1, String var2, String var3) {
			short var4 = this.getClass(var1);
			short var5 = this.getNameAndType(var2, var3);
			return this.getIndirect(new CodeGenerator.ConstantPool.IndirectEntry(9, var4, var5));
		}

		public short getMethodRef(String var1, String var2, String var3) {
			short var4 = this.getClass(var1);
			short var5 = this.getNameAndType(var2, var3);
			return this.getIndirect(new CodeGenerator.ConstantPool.IndirectEntry(10, var4, var5));
		}

		public short getInterfaceMethodRef(String var1, String var2, String var3) {
			short var4 = this.getClass(var1);
			short var5 = this.getNameAndType(var2, var3);
			return this.getIndirect(new CodeGenerator.ConstantPool.IndirectEntry(11, var4, var5));
		}

		public short getNameAndType(String var1, String var2) {
			short var3 = this.getUtf8(var1);
			short var4 = this.getUtf8(var2);
			return this.getIndirect(new CodeGenerator.ConstantPool.IndirectEntry(12, var3, var4));
		}

		public void setReadOnly() {
			this.readOnly = true;
		}

		public void write(OutputStream var1) throws IOException {
			DataOutputStream var2 = new DataOutputStream(var1);
			var2.writeShort(this.pool.size() + 1);
			for (Entry var4 : this.pool) {
				var4.write(var2);
			}
		}

		private short addEntry(CodeGenerator.ConstantPool.Entry var1) {
			this.pool.add(var1);
			if (this.pool.size() >= 65535) {
				throw new IllegalArgumentException("constant pool size limit exceeded");
			}
			else {
				return (short) this.pool.size();
			}
		}

		private short getValue(Object var1) {
			Short var2 = this.map.get(var1);
			if (var2 != null) {
				return var2;
			}
			else if (this.readOnly) {
				throw new InternalError("late constant pool addition: " + var1);
			}
			else {
				short var3 = this.addEntry(new CodeGenerator.ConstantPool.ValueEntry(var1));
				this.map.put(var1, var3);
				return var3;
			}
		}

		private short getIndirect(CodeGenerator.ConstantPool.IndirectEntry var1) {
			Short var2 = this.map.get(var1);
			if (var2 != null) {
				return var2;
			}
			else if (this.readOnly) {
				throw new InternalError("late constant pool addition");
			}
			else {
				short var3 = this.addEntry(var1);
				this.map.put(var1, var3);
				return var3;
			}
		}

		private static class IndirectEntry extends CodeGenerator.ConstantPool.Entry {

			private final int tag;

			private final short index0;

			private final short index1;

			public IndirectEntry(int var1, short var2) {
				super();
				this.tag = var1;
				this.index0 = var2;
				this.index1 = 0;
			}

			public IndirectEntry(int var1, short var2, short var3) {
				super();
				this.tag = var1;
				this.index0 = var2;
				this.index1 = var3;
			}

			public void write(DataOutputStream var1) throws IOException {
				var1.writeByte(this.tag);
				var1.writeShort(this.index0);
				if (this.tag == 9 || this.tag == 10 || this.tag == 11 || this.tag == 12) {
					var1.writeShort(this.index1);
				}
			}

			public int hashCode() {
				return this.tag + this.index0 + this.index1;
			}

			public boolean equals(Object var1) {
				if (var1 instanceof CodeGenerator.ConstantPool.IndirectEntry) {
					CodeGenerator.ConstantPool.IndirectEntry var2 = (CodeGenerator.ConstantPool.IndirectEntry) var1;
					return this.tag == var2.tag && this.index0 == var2.index0 && this.index1 == var2.index1;
				}
				return false;
			}

		}

		private static class ValueEntry extends CodeGenerator.ConstantPool.Entry {

			private final Object value;

			public ValueEntry(Object var1) {
				super();
				this.value = var1;
			}

			public void write(DataOutputStream var1) throws IOException {
				if (this.value instanceof String) {
					var1.writeByte(1);
					var1.writeUTF((String) this.value);
				}
				else if (this.value instanceof Integer) {
					var1.writeByte(3);
					var1.writeInt((Integer) this.value);
				}
				else if (this.value instanceof Float) {
					var1.writeByte(4);
					var1.writeFloat((Float) this.value);
				}
				else if (this.value instanceof Long) {
					var1.writeByte(5);
					var1.writeLong((Long) this.value);
				}
				else {
					if (!(this.value instanceof Double)) {
						throw new InternalError("bogus value entry: " + this.value);
					}
					var1.writeDouble(6.0D);
					var1.writeDouble((Double) this.value);
				}
			}

		}

		private abstract static class Entry {

			private Entry() {
			}

			public abstract void write(DataOutputStream var1) throws IOException;

		}

	}

	private static class PrimitiveTypeInfo {

		private static final Map<Class<?>, CodeGenerator.PrimitiveTypeInfo> table = new HashMap<>();

		static {
			add(Byte.TYPE, Byte.class);
			add(Character.TYPE, Character.class);
			add(Double.TYPE, Double.class);
			add(Float.TYPE, Float.class);
			add(Integer.TYPE, Integer.class);
			add(Long.TYPE, Long.class);
			add(Short.TYPE, Short.class);
			add(Boolean.TYPE, Boolean.class);
		}

		public String baseTypeString;

		public String wrapperClassName;

		public String wrapperValueOfDesc;

		public String unwrapMethodName;

		public String unwrapMethodDesc;

		private PrimitiveTypeInfo(Class<?> var1, Class<?> var2) {
			assert var1.isPrimitive();
			this.baseTypeString = Array.newInstance(var1, 0).getClass().getName().substring(1);
			this.wrapperClassName = CodeGenerator.dotToSlash(var2.getName());
			this.wrapperValueOfDesc = "(" + this.baseTypeString + ")L" + this.wrapperClassName + ";";
			this.unwrapMethodName = var1.getName() + "Value";
			this.unwrapMethodDesc = "()" + this.baseTypeString;
		}

		private static void add(Class<?> var0, Class<?> var1) {
			table.put(var0, new CodeGenerator.PrimitiveTypeInfo(var0, var1));
		}

		public static CodeGenerator.PrimitiveTypeInfo get(Class<?> var0) {
			return table.get(var0);
		}

	}

	private static class ExceptionTableEntry {

		public short startPc;

		public short endPc;

		public short handlerPc;

		public short catchType;

		public ExceptionTableEntry(short var1, short var2, short var3, short var4) {
			this.startPc = var1;
			this.endPc = var2;
			this.handlerPc = var3;
			this.catchType = var4;
		}

	}

	private class ProxyMethod {

		public String methodName;

		public Class<?>[] parameterTypes;

		public Class<?> returnType;

		public Class<?>[] exceptionTypes;

		public Class<?> fromClass;

		public String methodFieldName;

		private ProxyMethod(String methodName, Class<?>[] parameterTypes, Class<?> returnType,
				Class<?>[] exceptionTypes, Class<?> fromClass) {
			this.methodName = methodName;
			this.parameterTypes = parameterTypes;
			this.returnType = returnType;
			this.exceptionTypes = exceptionTypes;
			this.fromClass = fromClass;
			this.methodFieldName = "m" + CodeGenerator.this.proxyMethodCount++;
		}

		private CodeGenerator.MethodInfo generateMethod() throws IOException {
			String var1 = CodeGenerator.getMethodDescriptor(this.parameterTypes, this.returnType);
			CodeGenerator.MethodInfo methodInfo = CodeGenerator.this.new MethodInfo(this.methodName, var1, 17);
			int[] var3 = new int[this.parameterTypes.length];
			int var4 = 1;
			for (int var5 = 0; var5 < var3.length; ++var5) {
				var3[var5] = var4;
				var4 += CodeGenerator.getWordsPerType(this.parameterTypes[var5]);
			}

			byte var7 = 0;
			DataOutputStream outputStream = new DataOutputStream(methodInfo.code);
			CodeGenerator.this.code_aload(0, outputStream);
			outputStream.writeByte(180);
			outputStream.writeShort(CodeGenerator.this.cp.getFieldRef("java/lang/reflect/Proxy", "h",
					"Ljava/lang/reflect/InvocationHandler;"));
			CodeGenerator.this.code_aload(0, outputStream);
			outputStream.writeByte(178);
			outputStream.writeShort(
					CodeGenerator.this.cp.getFieldRef(CodeGenerator.dotToSlash(CodeGenerator.this.className),
							this.methodFieldName, "Ljava/lang/reflect/Method;"));
			if (this.parameterTypes.length > 0) {
				CodeGenerator.this.code_ipush(this.parameterTypes.length, outputStream);
				outputStream.writeByte(189);
				outputStream.writeShort(CodeGenerator.this.cp.getClass("java/lang/Object"));

				for (int var10 = 0; var10 < this.parameterTypes.length; ++var10) {
					outputStream.writeByte(89);
					CodeGenerator.this.code_ipush(var10, outputStream);
					this.codeWrapArgument(this.parameterTypes[var10], var3[var10], outputStream);
					outputStream.writeByte(83);
				}
			}
			else {
				outputStream.writeByte(1);
			}

			outputStream.writeByte(185);
			outputStream.writeShort(CodeGenerator.this.cp.getInterfaceMethodRef("java/lang/reflect/InvocationHandler",
					"invoke", "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"));
			outputStream.writeByte(4);
			outputStream.writeByte(0);
			if (this.returnType == Void.TYPE) {
				outputStream.writeByte(87);
				outputStream.writeByte(177);
			}
			else {
				this.codeUnwrapReturnValue(this.returnType, outputStream);
			}

			short var6;
			short var8 = var6 = (short) methodInfo.code.size();
			List<Class<?>> exceptionTypes = CodeGenerator.computeUniqueCatchList(this.exceptionTypes);
			if (exceptionTypes.size() > 0) {
				for (Class<?> exceptionType : exceptionTypes) {
					methodInfo.exceptionTable.add(new ExceptionTableEntry(var7, var8, var6,
							CodeGenerator.this.cp.getClass(CodeGenerator.dotToSlash(exceptionType.getName()))));
				}
				outputStream.writeByte(191);
				var6 = (short) methodInfo.code.size();
				methodInfo.exceptionTable.add(new CodeGenerator.ExceptionTableEntry(var7, var8, var6,
						CodeGenerator.this.cp.getClass("java/lang/Throwable")));
				CodeGenerator.this.code_astore(var4, outputStream);
				outputStream.writeByte(187);
				outputStream
						.writeShort(CodeGenerator.this.cp.getClass("java/lang/reflect/UndeclaredThrowableException"));
				outputStream.writeByte(89);
				CodeGenerator.this.code_aload(var4, outputStream);
				outputStream.writeByte(183);
				outputStream.writeShort(CodeGenerator.this.cp.getMethodRef(
						"java/lang/reflect/UndeclaredThrowableException", "<init>", "(Ljava/lang/Throwable;)V"));
				outputStream.writeByte(191);
			}

			if (methodInfo.code.size() > 65535) {
				throw new IllegalArgumentException("code size limit exceeded");
			}
			else {
				methodInfo.maxStack = 10;
				methodInfo.maxLocals = (short) (var4 + 1);
				methodInfo.declaredExceptions = new short[this.exceptionTypes.length];

				for (int var14 = 0; var14 < this.exceptionTypes.length; ++var14) {
					methodInfo.declaredExceptions[var14] = CodeGenerator.this.cp
							.getClass(CodeGenerator.dotToSlash(this.exceptionTypes[var14].getName()));
				}
				return methodInfo;
			}
		}

		private void codeWrapArgument(Class<?> var1, int var2, DataOutputStream outputStream) throws IOException {
			if (var1.isPrimitive()) {
				CodeGenerator.PrimitiveTypeInfo var4 = CodeGenerator.PrimitiveTypeInfo.get(var1);
				if (var1 != Integer.TYPE && var1 != Boolean.TYPE && var1 != Byte.TYPE && var1 != Character.TYPE
						&& var1 != Short.TYPE) {
					if (var1 == Long.TYPE) {
						CodeGenerator.this.code_lload(var2, outputStream);
					}
					else if (var1 == Float.TYPE) {
						CodeGenerator.this.code_fload(var2, outputStream);
					}
					else {
						if (var1 != Double.TYPE) {
							throw new AssertionError();
						}

						CodeGenerator.this.code_dload(var2, outputStream);
					}
				}
				else {
					CodeGenerator.this.code_iload(var2, outputStream);
				}

				outputStream.writeByte(184);
				outputStream.writeShort(
						CodeGenerator.this.cp.getMethodRef(var4.wrapperClassName, "valueOf", var4.wrapperValueOfDesc));
			}
			else {
				CodeGenerator.this.code_aload(var2, outputStream);
			}
		}

		private void codeUnwrapReturnValue(Class<?> aClass, DataOutputStream outputStream) throws IOException {
			if (aClass.isPrimitive()) {
				CodeGenerator.PrimitiveTypeInfo var3 = CodeGenerator.PrimitiveTypeInfo.get(aClass);
				outputStream.writeByte(192);
				outputStream.writeShort(CodeGenerator.this.cp.getClass(var3.wrapperClassName));
				outputStream.writeByte(182);
				outputStream.writeShort(CodeGenerator.this.cp.getMethodRef(var3.wrapperClassName, var3.unwrapMethodName,
						var3.unwrapMethodDesc));
				if (aClass != Integer.TYPE && aClass != Boolean.TYPE && aClass != Byte.TYPE && aClass != Character.TYPE
						&& aClass != Short.TYPE) {
					if (aClass == Long.TYPE) {
						outputStream.writeByte(173);
					}
					else if (aClass == Float.TYPE) {
						outputStream.writeByte(174);
					}
					else {
						if (aClass != Double.TYPE) {
							throw new AssertionError();
						}

						outputStream.writeByte(175);
					}
				}
				else {
					outputStream.writeByte(172);
				}
			}
			else {
				outputStream.writeByte(192);
				outputStream.writeShort(CodeGenerator.this.cp.getClass(CodeGenerator.dotToSlash(aClass.getName())));
				outputStream.writeByte(176);
			}
		}

		private void codeFieldInitialization(DataOutputStream outputStream) throws IOException {
			CodeGenerator.this.codeClassForName(this.fromClass, outputStream);
			CodeGenerator.this.code_ldc(CodeGenerator.this.cp.getString(this.methodName), outputStream);
			CodeGenerator.this.code_ipush(this.parameterTypes.length, outputStream);
			outputStream.writeByte(189);
			outputStream.writeShort(CodeGenerator.this.cp.getClass("java/lang/Class"));

			for (int var2 = 0; var2 < this.parameterTypes.length; ++var2) {
				outputStream.writeByte(89);
				CodeGenerator.this.code_ipush(var2, outputStream);
				if (this.parameterTypes[var2].isPrimitive()) {
					CodeGenerator.PrimitiveTypeInfo var3 = CodeGenerator.PrimitiveTypeInfo
							.get(this.parameterTypes[var2]);
					outputStream.writeByte(178);
					outputStream.writeShort(
							CodeGenerator.this.cp.getFieldRef(var3.wrapperClassName, "TYPE", "Ljava/lang/Class;"));
				}
				else {
					CodeGenerator.this.codeClassForName(this.parameterTypes[var2], outputStream);
				}
				outputStream.writeByte(83);
			}

			outputStream.writeByte(182);
			outputStream.writeShort(CodeGenerator.this.cp.getMethodRef("java/lang/Class", "getMethod",
					"(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"));
			outputStream.writeByte(179);
			outputStream.writeShort(
					CodeGenerator.this.cp.getFieldRef(CodeGenerator.dotToSlash(CodeGenerator.this.className),
							this.methodFieldName, "Ljava/lang/reflect/Method;"));
		}

	}

	private class MethodInfo {

		public int accessFlags;

		public String name;

		public String descriptor;

		public short maxStack;

		public short maxLocals;

		public ByteArrayOutputStream code = new ByteArrayOutputStream();

		public List<CodeGenerator.ExceptionTableEntry> exceptionTable = new ArrayList<>();

		public short[] declaredExceptions;

		public MethodInfo(String var2, String var3, int var4) {
			this.name = var2;
			this.descriptor = var3;
			this.accessFlags = var4;
			CodeGenerator.this.cp.getUtf8(var2);
			CodeGenerator.this.cp.getUtf8(var3);
			CodeGenerator.this.cp.getUtf8("Code");
			CodeGenerator.this.cp.getUtf8("Exceptions");
		}

		public void write(DataOutputStream var1) throws IOException {
			var1.writeShort(this.accessFlags);
			var1.writeShort(CodeGenerator.this.cp.getUtf8(this.name));
			var1.writeShort(CodeGenerator.this.cp.getUtf8(this.descriptor));
			var1.writeShort(2);
			var1.writeShort(CodeGenerator.this.cp.getUtf8("Code"));
			var1.writeInt(12 + this.code.size() + 8 * this.exceptionTable.size());
			var1.writeShort(this.maxStack);
			var1.writeShort(this.maxLocals);
			var1.writeInt(this.code.size());
			this.code.writeTo(var1);
			var1.writeShort(this.exceptionTable.size());

			for (ExceptionTableEntry var3 : this.exceptionTable) {
				var1.writeShort(var3.startPc);
				var1.writeShort(var3.endPc);
				var1.writeShort(var3.handlerPc);
				var1.writeShort(var3.catchType);
			}
			var1.writeShort(0);
			var1.writeShort(CodeGenerator.this.cp.getUtf8("Exceptions"));
			var1.writeInt(2 + 2 * this.declaredExceptions.length);
			var1.writeShort(this.declaredExceptions.length);
			short[] var6 = this.declaredExceptions;
			for (short var5 : var6) {
				var1.writeShort(var5);
			}
		}

	}

	private class FieldInfo {

		public int accessFlags;

		public String name;

		public String descriptor;

		public FieldInfo(String var2, String var3, int var4) {
			this.name = var2;
			this.descriptor = var3;
			this.accessFlags = var4;
			CodeGenerator.this.cp.getUtf8(var2);
			CodeGenerator.this.cp.getUtf8(var3);
		}

		public void write(DataOutputStream var1) throws IOException {
			var1.writeShort(this.accessFlags);
			var1.writeShort(CodeGenerator.this.cp.getUtf8(this.name));
			var1.writeShort(CodeGenerator.this.cp.getUtf8(this.descriptor));
			var1.writeShort(0);
		}

	}

}
