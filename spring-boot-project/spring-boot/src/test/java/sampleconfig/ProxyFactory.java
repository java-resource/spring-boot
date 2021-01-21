package sampleconfig;

import sun.misc.ProxyGenerator;

import java.io.FileOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

interface Calculator {

	// 需要代理的接口
	public int add(int a, int b);

	// 接口实现类,执行真正的a+b操作
	class CalculatorImpl implements Calculator {

		@Override
		public int add(int a, int b) {
			System.out.println("doing ");
			return a + b;
		}

	}

	// 静态代理类的实现.代码已经实现好了.
	class CalculatorProxy implements Calculator {

		private final Calculator calculator;

		public CalculatorProxy(Calculator calculator) {
			this.calculator = calculator;
		}

		@Override
		public int add(int a, int b) {
			// 执行一些操作
			System.out.println("begin ");
			int result = calculator.add(a, b);
			System.out.println("end ");
			return result;
		}

	}

}

public class ProxyFactory implements InvocationHandler {

	private final Class<?> target;

	private Object real;

	// 委托类class
	public ProxyFactory(Class<?> target) {
		this.target = target;
	}

	/**
	 * ${@link sun.misc.Launcher}
	 */
	public static void main(String[] args) throws Exception {
		Calculator proxy = (Calculator) new ProxyFactory(Calculator.class).bind(new Calculator.CalculatorImpl());
		System.out.println(proxy.add(1, 2));
		byte[] classFile = ProxyGenerator.generateProxyClass("CalculatorProxy.class", new Class[] { proxy.getClass() });
		String paths = Calculator.class.getResource(".").getPath();
		System.out.println(paths);
		FileOutputStream out = new FileOutputStream(paths + "CalculatorProxy.class");
		out.write(classFile);
		out.flush();
		out.close();
	}

	// 实际执行类bind
	public Object bind(Object real) {
		this.real = real;
		// 利用JDK提供的Proxy实现动态代理
		return Proxy.newProxyInstance(target.getClassLoader(), new Class[] { target }, this);
	}

	@Override
	public Object invoke(Object o, Method method, Object[] args) throws Throwable {
		// 代理环绕
		System.out.println("begin");
		// 执行实际的方法
		Object invoke = method.invoke(real, args);
		System.out.println("end");
		return invoke;
	}

}