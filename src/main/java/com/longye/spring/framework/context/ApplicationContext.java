package com.longye.spring.framework.context;

import com.longye.spring.framework.annotation.MyAutowired;
import com.longye.spring.framework.annotation.MyController;
import com.longye.spring.framework.annotation.MyService;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by tianl on 2018/10/17.
 */
public class ApplicationContext {

    //IOC容器
    private static ConcurrentHashMap<String,Object> IOC = new ConcurrentHashMap<>();

    //用来存放class文件名称的容器。比如com.longye.spring.demo.controller.TestController
    private static List<String> classList = new ArrayList<>();

    private static Properties properties = new Properties();

    /**
     * 初始化IOC容器
     * @param location
     */
    public static void initIOC(String location){

        InputStream is = null;

        try {
            //1、定位需要解析的配置文件
            is = ApplicationContext.class.getClassLoader().getResourceAsStream(location);

            //2、加载该配置文件
            Properties properties = getProperties();
            properties.load(is);

            //3、获取扫描包下所有的类文件的名字
            String scanPackage = properties.getProperty("scanPackage");
            doRegister(scanPackage);

            //4、实例化所有带MyController和MyService注解的类至IOC容器
            doCreateBean();

            //5、实现依赖注入
            doPopulate();

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("IOC容器已经初始化完成......");
    }

    /**
     * 依赖注入,对IOC容器里的bean中添加MyAutowired注解的属性进行赋值
     */
    private static void doPopulate() {

        if(IOC.isEmpty()){
            return;
        }

        for (Map.Entry<String, Object> entry : IOC.entrySet()) {

            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(field.isAnnotationPresent(MyAutowired.class)){
                    MyAutowired myAutowired = field.getAnnotation(MyAutowired.class);
                    String value = myAutowired.value();
                    if(value.equals("")){//说明未自定义,是使用的接口名来作为的key
                        value = field.getType().getName();
                    }

                    //开发私有属性的访问权限
                    field.setAccessible(true);

                    try {
                        field.set(entry.getValue(),IOC.get(value));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 实例化所有需要实例化的bean(带MyController和MyService注解的)至IOC容器中
     */
    private static void doCreateBean() {

        try {
            for (String className : classList) {
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)){
                    //IOC中的bean对应的key。这里规则是类名首字母小写。比如testController
                    String key = lowerFirstChar(clazz.getSimpleName());
                    IOC.put(key, clazz.newInstance());
                }

                if(clazz.isAnnotationPresent(MyService.class)){
                    MyService myService = clazz.getAnnotation(MyService.class);
                    //因为我们注入业务类的bean至其他类中时,一般注入的都是接口。比如：@MyAutowired private AppleService appleService
                    //所以当该bean自定义了名称时,则使用该名称作为该bean的key.当其他类中注入该bean的时候,MyAutowired注解需要指定该名称
                    //当该bean没有自定义名称时,我们需要把该bean的接口名称作为该bean的key。这样依赖注入的时候就可以根据该接口名获取到IOC容器中bean
                    String value = myService.value();
                    if(!value.equals("")){
                        IOC.put(value, clazz.newInstance());
                        continue;
                    }

                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        //key = com.longye.spring.demo.service.AppleService
                        String key = i.getName();
                        IOC.put(key, clazz.newInstance());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册需要扫描的路径下的所有类的名称至容器classList
     * @param scanPackage 待扫描的路径
     */
    private static void doRegister(String scanPackage) {

        //scanPackage = com.longye.spring.demo
        //packageFile = com/longye/spring/demo
        String packageFile = scanPackage.replaceAll("\\.", "/");

        //url = file:/D:/writeBySelf/my_spring/target/classes/com/longye/spring/demo
        //需要注意的是packageFile下必须要有文件,否则获取到的URL为null
        URL url = ApplicationContext.class.getClassLoader().getResource(packageFile);

        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if(file.isDirectory()){
                doRegister(scanPackage + "." + file.getName());
            }else{
                //className = TestController.class
                String className = file.getName();
                classList.add(scanPackage + "." + className.replaceAll(".class", ""));
            }
        }
    }

    private static String lowerFirstChar(String letter) {

        char[] chars = letter.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    public static ConcurrentHashMap<String, Object> getIOC() {
        return IOC;
    }

    public static Properties getProperties() {
        return properties;
    }
}
