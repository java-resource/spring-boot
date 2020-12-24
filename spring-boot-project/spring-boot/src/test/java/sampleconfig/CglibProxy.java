package sampleconfig;


import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;


public class CglibProxy {
	public static void main(String[] args) {
		String cglibPath = CglibProxy.class.getResource(".").getPath();
		System.out.println(cglibPath);
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, cglibPath);
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(Animal.class);
		//类似invokerhanddler的invoke方法
		enhancer.setCallback((MethodInterceptor) (o, method, objects, methodProxy) -> {
			System.out.println("begin");
			Object invoke = methodProxy.invoke(new Animal(), objects);
			System.out.println("end");
			return invoke;
		});
		Animal proxy = (Animal) enhancer.create();
		proxy.run();
	}
}

class Animal {
	void run() {
		System.out.println("runing");
	}
}