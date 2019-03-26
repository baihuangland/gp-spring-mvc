package com.gupaoedu.framework.servlet.v3;

import com.gupaoedu.framework.annotation.*;
import com.gupaoedu.framework.conver.Conver;
import com.gupaoedu.framework.conver.ConverFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public final class GPDispatcherServlet extends HttpServlet {


    // 配置文件路径key
    private static final String CONTEXT_CONFIG_LOCATION = "contextConfigLocation";


    // 扫描的包路径key
    private static final String SCAN_PACKAGE = "scanPackage";


    // 配置变量
    private final Properties properties = new Properties();

    // 扫描包下的所有类的全名称
    private final List<String> classNames = new ArrayList<>();


    // IOC容器，使用线程安全的map
    private final Map<String, Object> beanMap = new ConcurrentHashMap<>();

    private final List<HandlerMapping> handlerMappings = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }


    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        HandlerMapping handlerMapping = getHandler(req, resp);
        if (handlerMapping == null) {
            try {
                resp.getWriter().write("404 NOT FOUND");
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        doInvoke(handlerMapping, req, resp);
    }

    // 执行方法
    private void doInvoke(HandlerMapping handlerMapping, HttpServletRequest req, HttpServletResponse resp) {
        Object[] params = doInitParams(handlerMapping, req, resp);
        try {
            Object result = handlerMapping.getMethod().invoke(handlerMapping.getController(), params);
            if (result != null) {
                resp.getWriter().write(result.toString());
            }

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Object[] doInitParams(HandlerMapping handlerMapping, HttpServletRequest req, HttpServletResponse resp) {
        List<HandlerMapping.ParamMapping> paramMappings = handlerMapping.getParamMappings();
        Object[] params = new Object[paramMappings.size()];
        Map<String, String[]> parameterMap = req.getParameterMap();
        for (HandlerMapping.ParamMapping paramMapping : paramMappings) {
            int index = paramMapping.getIndex();
            String paramName = paramMapping.getParamName();
            if (HttpServletRequest.class.getSimpleName().equals(paramName)) {
                params[index] = req;
                continue;
            }
            if (HttpServletResponse.class.getSimpleName().equals(paramName)) {
                params[index] = resp;
                continue;
            }

            String[] values = parameterMap.get(paramName);
            Object result = null;
            if (values != null && values.length > 0) {
                Conver conver = ConverFactory.getConver(paramMapping.getParamType());
                if (conver == null) {
                    throw new RuntimeException("CAN NOT GET CONVER FOR" + paramMapping.getParamType());
                }
                String str = arrayToString(values);
                result = conver.cover(str);
            }
            params[index] = result;


        }

        return params;
    }

    private String arrayToString(String[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            stringBuilder.append(values[i]);
            if (i < values.length - 1) {
                stringBuilder.append(",");
            }
        }
        return stringBuilder.toString();
    }


    private HandlerMapping getHandler(HttpServletRequest req, HttpServletResponse resp) {
        if (handlerMappings.isEmpty()) {
            return null;
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        // 替换项目名，并将多/替换为一个/
        String mappingUrl = url.replace(contextPath, "").replaceAll("/+", "/");
        for (HandlerMapping handlerMapping : handlerMappings) {
            if (handlerMapping.getUrl().equals(mappingUrl)) {
                return handlerMapping;
            }
        }
        return null;

    }

    @Override
    public void init(ServletConfig config) throws ServletException {

        // 加载配置文件
        doLoadConfig(config.getInitParameter(CONTEXT_CONFIG_LOCATION));

        // 扫描配置的包路径
        doScanner(properties.getProperty(SCAN_PACKAGE));

        // 实例化bean
        doInstance();

        // 初始化bean的变量(自动注入)
        doAurowired();

        // 初始化url，method映射
        doInitHandlerMapping();
    }

    private void doInitHandlerMapping() {
        // IOC容器为空，返回
        if (beanMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
            // 获取方法上注解进行映射
            Object bean = entry.getValue();
            Class<?> clazz = bean.getClass();
            // 只扫描被GPController标注的方法
            if (!clazz.isAnnotationPresent(GPController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                GPRequestMapping gpRequestMapping = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = gpRequestMapping.value().trim();
            }

            // 获取所有的方法(只获取public的方法)
            for (Method method : clazz.getMethods()) {
                // 只映射被GPRequestMapping注解的方法
                if (!method.isAnnotationPresent(GPRequestMapping.class)) {
                    continue;
                }
                GPRequestMapping gpRequestMapping = method.getAnnotation(GPRequestMapping.class);

                // 构建url，自定填充/,并将连续的多个/替换成一个/
                String url = ("/" + baseUrl + "/" + gpRequestMapping.value().trim()).replaceAll("/+", "/");
                HandlerMapping handlerMapping = new HandlerMapping(url, method, bean);
                handlerMappings.add(handlerMapping);
            }


        }


    }

    private void doAurowired() {
        // 当IOC容器为空时，不需要自动注入
        if (beanMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : beanMap.entrySet()) {

            // 获取所有的变量
            for (Field field : entry.getValue().getClass().getDeclaredFields()) {
                //只给有注解的变量初始化
                if (!field.isAnnotationPresent(GPAutowired.class)) {
                    continue;
                }

                GPAutowired gpAutowired = field.getAnnotation(GPAutowired.class);

                String beanName = gpAutowired.value().trim();
                // 没有定义beanName
                if ("".equals(beanName)) {
                    // 采用lei名称作为beanName（首字母小写）
                    beanName = toLowerFirstCase(field.getType().getSimpleName());
                }
                // IOC容器不包含beanName，则采用全类名
                if (!beanMap.containsKey(beanName)) {
                    beanName = field.getType().getName();
                }
                Object bean = beanMap.get(beanName);
                // 强制访问
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(), bean);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doInstance() {
        // 没有类文件，直接返回
        if (classNames.isEmpty()) {
            return;
        }

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                // 只实例化有注解的类
                if (clazz.isAnnotationPresent(GPController.class)) {
                    Object bean = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    if (beanMap.containsKey(beanName)) {
                        throw new RuntimeException("不能定义重复的beanName:" + beanName);
                    }
                    beanMap.put(beanName, bean);
                    continue;
                }
                if (clazz.isAnnotationPresent(GPService.class)) {
                    Object bean = clazz.newInstance();
                    GPService gpService = clazz.getAnnotation(GPService.class);
                    // 默认使用注解中定义的beanName
                    String beanName = gpService.value().trim();
                    if ("".equals(beanName)) {
                        // 没有自定义beanName时，则采用类名首字母先写作为bean的名称
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    if (beanMap.containsKey(beanName)) {
                        throw new RuntimeException("不能定义重复的beanName:" + beanName);
                    }
                    beanMap.put(beanName, bean);

                    // 采用投机的方式，将bean的接口全类名作为key，方便注入
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (i == Serializable.class) {
                            continue;
                        }
                        beanName = i.getName();
                        if (beanMap.containsKey(beanName)) {
                            throw new RuntimeException("不能定义重复的beanName:" + beanName);
                        }
                        beanMap.put(beanName, bean);
                    }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        // 首字母大写
        if (chars[0] <= 90) {
            chars[0] += 32;
        }
        return String.valueOf(chars);

    }

    private void doScanner(String scanPackage) {

        // 获取扫描的文件url
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File file = new File(url.getFile());

        for (File f : file.listFiles()) {
            String fileName = f.getName();
            if (f.isDirectory()) {
                doScanner(scanPackage + "." + fileName);
            } else {
                // 只要class文件
                if (fileName.endsWith(".class")) {
                    // 全类名
                    String className = scanPackage + "." + fileName.replace(".class", "");
                    classNames.add(className);
                }
            }
        }
    }

    private void doLoadConfig(String configLocation) {

        configLocation = configLocation.replace("classpath:", "");
        InputStream is = null;

        try {
            is = this.getClass().getClassLoader().getResourceAsStream(configLocation);
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭资源
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    private class HandlerMapping {

        private String url;

        private Method method;

        private Object controller;

        private List<ParamMapping> paramMappings;

        private HandlerMapping(String url, Method method, Object controller) {
            this.url = url;
            this.method = method;
            this.controller = controller;

            paramMappings = new ArrayList<>();
            // 一维数据的长度为方法的参数个数（没有注解的变量也可以获取到）

            Class<?>[] parameterTypes = method.getParameterTypes();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof GPRequestParam) {
                        GPRequestParam gpRequestParam = (GPRequestParam) annotation;
                        String paramName = gpRequestParam.value();
                        // 跳出内层循环
                        ParamMapping parammMapping = new ParamMapping(i, parameterTypes[i], paramName);
                        paramMappings.add(parammMapping);
                        break;
                    }
                }
            }


            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i] == HttpServletRequest.class) {
                    String paramName = HttpServletRequest.class.getName();
                    ParamMapping parammMapping = new ParamMapping(i, parameterTypes[i], paramName);
                    paramMappings.add(parammMapping);
                    continue;
                }

                if (parameterTypes[i] == HttpServletResponse.class) {
                    String paramName = HttpServletResponse.class.getName();
                    ParamMapping parammMapping = new ParamMapping(i, parameterTypes[i], paramName);
                    paramMappings.add(parammMapping);
                    continue;
                }
            }
        }

        public String getUrl() {
            return url;
        }


        public Method getMethod() {
            return method;
        }


        public Object getController() {
            return controller;
        }

        public List<ParamMapping> getParamMappings() {
            return paramMappings;
        }


        private class ParamMapping {
            private int index;
            private Class<?> paramType;
            private String paramName;

            private ParamMapping(int index, Class<?> paramType, String paramName) {
                this.index = index;
                this.paramType = paramType;
                this.paramName = paramName;
            }

            public int getIndex() {
                return index;
            }

            public Class<?> getParamType() {
                return paramType;
            }

            public String getParamName() {
                return paramName;
            }
        }
    }


}
