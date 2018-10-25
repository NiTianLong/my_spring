package com.longye.spring.framework.servlet;

import com.longye.spring.framework.annotation.MyController;
import com.longye.spring.framework.annotation.MyRequestMapping;
import com.longye.spring.framework.annotation.MyRequestParam;
import com.longye.spring.framework.context.ApplicationContext;
import com.longye.spring.view.MyModelAndView;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by tianl on 2018/10/17.
 */
public class MyDispatcherServlet extends HttpServlet{

    //这里的值就是web.xml文件中的<param-name>
    private static final String LOCATION = "contextConfigLocation";

    //本项目中所有静态文件存放的文件位置
    private static final String VIEWS = "views";

    //handlerMapping容器,存储url和method的对应关系
    private List<MyHandler> handlerMapping = new ArrayList<>();

    //adapterMapping容器,方法适配,匹配方法的参数
    private Map<MyHandler, MyAdapter> adapterMapping = new HashMap<>();

    //存储所有的静态文件
    private List<ViewResolver> viewResolvers = new ArrayList<ViewResolver>();

    @Override
    public void init(ServletConfig config) throws ServletException {

        //初始化IOC容器
        ApplicationContext.initIOC(config.getInitParameter(LOCATION));

        //映射相应的url和它请求的方法
        initHandlerMapping();

        //匹配请求接口方法的参数
        initAdapterMapping();

        //加载所有的静态页面,比如我们之前的jsp文件,这里我们来自己定义一种文件类型.jspk文件
        initViewResolvers();

        System.out.println("MySpring 已经初始化完成......");

    }

    /**
     * 加载所有的静态页面,比如我们之前的jsp文件,这里我们来自己定义一种文件类型.jspk文件
     */
    private void initViewResolvers() {

        //为了避免我们的静态文件被直接访问到,所以静态文件一般是放在WEB-INF文件下
        //这里我们为了方便获取,放在resources文件下
        String viewUrl = ApplicationContext.getProperties().getProperty(VIEWS);

        URL url = this.getClass().getClassLoader().getResource(viewUrl);
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            viewResolvers.add(new ViewResolver(file.getName(), file));
        }
    }

    /**
     * 匹配请求接口方法的参数
     */
    private void initAdapterMapping() {

        if(handlerMapping.isEmpty()){
            return;
        }

        for (MyHandler handler : handlerMapping) {
            //为了方便,Controller类中的方法的参数前都添加了@MyRequestParam注解且注解中都定义了参数名,所以这里直接使用getParameterAnnotations来获取参数前的注解
            //通过注解获取参数的名称
            //因为参数前可以添加多个注解,所以getParameterAnnotations获取的是一个二维数组
            Annotation[][] parameterAnnotations = handler.method.getParameterAnnotations();

            //存储方法参数中的参数名和参数位置  key为参数名 value为该参数是该方法中的第几个参数
            Map<String, Integer> paramMapping = new HashMap<>();

            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    Class<? extends Annotation> clazz = annotation.annotationType();
                    if(clazz == MyRequestParam.class){
                        String paramName = ((MyRequestParam) annotation).value();
                        paramMapping.put(paramName, i);
                    }
                }
            }

            adapterMapping.put(handler, new MyAdapter(paramMapping));
        }

    }

    /**
     * 映射相应的url和它请求的方法
     */
    private void initHandlerMapping() {

        ConcurrentHashMap<String, Object> ioc = ApplicationContext.getIOC();
        if(ioc.isEmpty()){
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){
                continue;
            }
            String url = "";
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                String value = requestMapping.value();
                url = value;
            }

            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if(method.isAnnotationPresent(MyRequestMapping.class)){
                    MyRequestMapping myRequestMapping = method.getAnnotation(MyRequestMapping.class);
                    String value = myRequestMapping.value();
                    //regx = /test/fruit
                    String regx = url + value;
                    Pattern pattern = Pattern.compile(regx);
                    handlerMapping.add(new MyHandler(pattern,entry.getValue(),method));
                }
            }

        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            doDispatcher(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500 exception, Msg:" + Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * 执行请求的接口
     * @param req
     * @param resp
     */
    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception{

        //获取请求url对应的MyHandler
        MyHandler handler = getHandler(req);
        if(null == handler){
            resp.getWriter().write("404 Not Found...");
            return;
        }

        //获取存储方法参数对应关系的MyAdapter
        MyAdapter adapter = getAdapter(handler);
        if(null == adapter){
            return;
        }

        //获取url请求的接口参数的值的数组,作为后续反射执行该方法时的传参
        Object[] paramValues = getMethodRealValue(req, resp, handler, adapter);

        //反射执行方法
        MyModelAndView mv = invokeMethod(handler, paramValues);

        //如果MyModelAndView不为null,则跳转至要打开的页面,并解析后端传给前端的数据
        applyDefaultViewName(resp, mv);
    }

    /**
     * 解析并展示前端页面
     * @param resp
     * @param mv
     */
    private void applyDefaultViewName(HttpServletResponse resp, MyModelAndView mv) throws Exception {

        if(null == mv || viewResolvers.isEmpty()){
            return;
        }

        for (ViewResolver viewResolver : viewResolvers) {

            if(mv.getViewName().equals(viewResolver.getViewName())){

                String str = parseView(mv, viewResolver);
                if(null != str && !str.equals("")){
                    resp.getWriter().write(str);
                    break;
                }
            }
        }
    }

    /**
     * 页面模板,模板框架很复杂,但是原理都是一样的,通过正则表达式来解析
     * @param mv
     * @return
     */
    private String parseView(MyModelAndView mv, ViewResolver viewResolver) throws Exception {

        //我们知道jsp中通过${xxx}来获取后端传过来的数据
        //但是这里我们是自己定义的页面格式.jspk。我们通过@{xxx}来获取后端传来的数据
        StringBuffer sb = new StringBuffer();
        RandomAccessFile raf = new RandomAccessFile(viewResolver.getViewFile(), "r");
        String line = null;
        try {
            while (null != (line = raf.readLine())){
                Matcher matcher = matcher(line);
                while (matcher.find()){
                    //这里注意使用了 i <= 而不是 i < 。需要空闲的时候在看看正则表达式
                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        // paramName = @{xxx}
                        String paramName = matcher.group(i);
                        //对于@{xxx}。因为{和}是正则语法中的一部分,所以这里我们需要转义。所以我要取出xxx按正则表达式拼接在一起
                        paramName = paramName.substring(2, paramName.length());//去掉@{
                        paramName = paramName.substring(0, paramName.length() - 1);//去掉}

                        Object paramValue = mv.getModel().get(paramName);
                        line = line.replaceAll("@\\{" + paramName + "\\}", paramValue.toString());
                    }
                }
                sb.append(line);
            }
        } finally {
            raf.close();
        }

        return sb.toString();
    }

    /**
     * 解析页面的正则
     * @param line
     * @return
     */
    private Matcher matcher(String line) {
        //为了简单,line中的@{xxx}必须都是完整的
        Pattern pattern = Pattern.compile("@\\{[^{]*\\}", Pattern.CASE_INSENSITIVE);//CASE_INSENSITIVE是忽略大小写
        Matcher matcher = pattern.matcher(line);
        return matcher;
    }

    /**
     * 执行方法,返回 ModelAndView
     * @param handler
     * @param paramValues
     * @return
     * @throws Exception
     */
    private MyModelAndView invokeMethod(MyHandler handler, Object[] paramValues) throws Exception {

        Object obj = handler.method.invoke(handler.controller, paramValues);

        boolean bool = handler.method.getReturnType() == MyModelAndView.class;
        if(bool){
            return (MyModelAndView) obj;
        }
        return null;
    }


    /**
     * 获取url请求的接口参数的值的数组,作为后续反射执行该方法时的传参
     * @param req
     * @param resp
     * @param handler
     * @param adapter
     * @return
     */
    private Object[] getMethodRealValue(HttpServletRequest req, HttpServletResponse resp, MyHandler handler, MyAdapter adapter) {

        //获取参数的类型是String还是Integer还是....
        Class<?>[] parameterTypes = handler.method.getParameterTypes();

        //存储前端传来的参数的真实值
        Object[] paramValues = new Object[parameterTypes.length];

        //前端传来的参数的Map集合,因为参数可能是数组,所以用数组作为value
        Map<String, String[]> parameterMap = req.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            //value = [apple, orange]
            String value = Arrays.toString(entry.getValue());
            //paramValue = apple,orange
            String paramValue = value.replaceAll("\\[|\\]","").replaceAll(",\\s", ",");

            Map<String, Integer> paramMapping = adapter.paramMapping;
            Integer index = adapter.paramMapping.get(entry.getKey());

            paramValues[index] = castValue(paramValue, parameterTypes[index]);

        }

        return paramValues;

    }

    /**
     * 根据参数实际类型进行转换
     * @param value
     * @param parameterType
     * @return
     */
    private Object castValue(String value, Class<?> parameterType) {

        if(parameterType == String.class){
            return String.valueOf(value);
        }else if(parameterType == Integer.class){
            return Integer.valueOf(value);
        }
        return null;
    }

    /**
     * 获取方法的参数的对应关系
     * @param handler
     */
    private MyAdapter getAdapter(MyHandler handler) {

        return adapterMapping.get(handler);
    }

    /**
     * 获取请求url对应的handler
     * @param req
     */
    private MyHandler getHandler(HttpServletRequest req) {

        if(handlerMapping.isEmpty()){
            return null;
        }

        String url = req.getRequestURI();
        for (MyHandler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if(matcher.matches()){
                return handler;
            }
        }

        return null;
    }

    /**
     * handlerMapping容器,存储url和method的对应关系
     */
    private class MyHandler {

        private Pattern pattern;//url的正则

        private Object controller;//method方法所在的对象

        private Method method;//method方法

        public MyHandler(Pattern pattern, Object controller, Method method) {
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;
        }
    }

    /**
     * 方法适配器
     */
    private class MyAdapter {

        //存储方法参数中的参数名和参数位置  key为参数名 value为该参数是该方法中的第几个参数
        private Map<String, Integer> paramMapping = new HashMap<>();

        public MyAdapter(Map<String, Integer> paramMapping) {
            this.paramMapping = paramMapping;
        }
    }

    /**
     * 存储所有静态文件
     */
    private class ViewResolver {

        private String viewName;

        private File viewFile;

        public ViewResolver(String viewName, File viewFile) {
            this.viewName = viewName;
            this.viewFile = viewFile;
        }

        public String getViewName() {
            return viewName;
        }

        public File getViewFile() {
            return viewFile;
        }
    }
}
