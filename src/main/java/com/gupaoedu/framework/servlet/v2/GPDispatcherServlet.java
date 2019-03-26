package com.gupaoedu.framework.servlet.v2;


import com.gupaoedu.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GPDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private final String contextConfigLocation = "contextConfigLocation";

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> beanMap = new ConcurrentHashMap<>(32);

    private Map<String, Method> handlerMappings = new ConcurrentHashMap<>(32);


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        if (handlerMappings.isEmpty()) {
            try {
                resp.getWriter().write("404");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String uri = req.getRequestURI();

        String contextPath = req.getContextPath();

        String url = uri.replace(contextPath, "");

        if (!handlerMappings.containsKey(url)) {
            try {
                resp.getWriter().write("404");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Method method = handlerMappings.get(url);
        Object[] obj = doInitRequestParam(req, resp, method);

        try {
            String beanName = method.getDeclaringClass().getSimpleName();
            Object bean = beanMap.get(toLowerFirstCase(beanName));
            Object response = method.invoke(bean, obj);

            resp.getWriter().write(String.valueOf(response));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private Object[] doInitRequestParam(HttpServletRequest request, HttpServletResponse response, Method method) {
        Map<String, String[]> paramMap = request.getParameterMap();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] param = new Object[parameterTypes.length];


        // 没有加注解的参数也可以获取到
        Annotation[][] classes = method.getParameterAnnotations();
        for (int j = 0; j < classes.length; j++) {
            for (Annotation annotation : classes[j]) {
                if (annotation instanceof GPRequestParam) {
                    GPRequestParam gpRequestParam = (GPRequestParam) annotation;
                    String paramName = gpRequestParam.value();
                    if (paramMap.containsKey(paramName)) {
                        String[] values = paramMap.get(paramName);
                        String value = Arrays.toString(values)
                                .replaceAll("\\[|\\]", "").replaceAll("\\s+", ",");
                        Object val = cover(parameterTypes[j], value);
                        param[j] = val;

                        // 跳出内层循环
                        break;
                    }
                }
            }
        }


        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                param[i] = request;
                continue;
            }

            if (parameterType == HttpServletResponse.class) {
                param[i] = response;
                continue;
            }

        }

        return param;
    }


    private Object cover(Class<?> parameterType, String value) {
        if (parameterType == int.class) {
            return Integer.parseInt(value);
        } else if (parameterType == Integer.class) {
            return Integer.parseInt(value);
        } else if (parameterType == Double.class) {
            return Double.parseDouble(value);
        } else if (parameterType == Float.class) {
            return Float.parseFloat(value);
        }
        return value;
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter(contextConfigLocation));

        // 2.扫描相应的包
        doScanner(properties.getProperty("scanPackage"));

        // 3.初始化
        doInstance();

        //4. 依赖注入
        doAutowired();

        //4.初始化关系映射
        doIntihandlerMapping();
    }

    private void doIntihandlerMapping() {
        if (beanMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GPController.class)) {
                continue;
            }

            String baseUrl = "";

            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                baseUrl = clazz.getAnnotation(GPRequestMapping.class).value().trim();
            }

            // 只获取public的方法
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(GPRequestMapping.class)) {
                    continue;
                }

                GPRequestMapping gpRequestMapping = method.getAnnotation(GPRequestMapping.class);

                String url = ("/" + baseUrl + "/" + gpRequestMapping.value().trim()).replaceAll("/+", "/");

                handlerMappings.put(url, method);
            }


        }

    }

    private void doAutowired() {
        if (beanMap.isEmpty()) {
            return;

        }

        for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            for (Field field : clazz.getDeclaredFields()) {
                if (!field.isAnnotationPresent(GPAutowired.class)) {
                    continue;
                }

                GPAutowired gpAutowired = field.getAnnotation(GPAutowired.class);

                String beanName = gpAutowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = toLowerFirstCase(field.getType().getSimpleName());
                }


                if (!beanMap.containsKey(beanName)) {
                    beanName = field.getType().getName();
                }

                field.setAccessible(true);

                try {
                    field.set(entry.getValue(), beanMap.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    private void doInstance() {

        if (classNames.isEmpty()) {
            return;
        }

        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GPController.class)) {
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    if (beanMap.containsKey(beanName)) {
                        throw new RuntimeException(beanName + "已存在");
                    }
                    Object bean = clazz.newInstance();
                    beanMap.put(beanName, bean);

                } else if (clazz.isAnnotationPresent(GPService.class)) {

                    GPService gpService = clazz.getAnnotation(GPService.class);

                    String beanName = gpService.value().trim();

                    if ("".equals(beanName)) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    if (beanMap.containsKey(beanName)) {
                        throw new RuntimeException(beanName + "已存在");
                    }

                    Object bean = clazz.newInstance();

                    beanMap.put(beanName, bean);

                    for (Class<?> i : clazz.getInterfaces()) {
                        String interfaceName = i.getName();
                        if (beanMap.containsKey(interfaceName)) {
                            throw new RuntimeException("存在两个bean实现同一个接口：" + interfaceName);
                        }

                        beanMap.put(interfaceName, bean);

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

    private void doScanner(String scanpackage) {
        if ("".equals(scanpackage)) {
            throw new RuntimeException("请配置包扫描路径");
        }

        URL url = this.getClass().getClassLoader().getResource("/" + scanpackage.replaceAll("\\.", "/"));

        File file = new File(url.getFile());

        for (File file1 : file.listFiles()) {

            if (file1.isDirectory()) {
                this.doScanner(scanpackage + "." + file1.getName());
            } else {
                String fileName = file1.getName();
                if (fileName.endsWith(".class")) {
                    String className = fileName.replace(".class", "");
                    classNames.add(scanpackage + "." + className);
                }
            }
        }

    }

    private void doLoadConfig(String location) {
        InputStream inputStream = null;
        try {
            inputStream = this.getClass().getClassLoader().getResourceAsStream(location.replace("classpath:", ""));

            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    private String toLowerFirstCase(String beanName) {
        char[] chars = beanName.toCharArray();
        chars[0] += 32;
        return new String(chars);
    }


}
