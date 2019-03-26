package com.gupaoedu.mvc.controller;

import com.gupaoedu.framework.annotation.GPAutowired;
import com.gupaoedu.framework.annotation.GPController;
import com.gupaoedu.framework.annotation.GPRequestMapping;
import com.gupaoedu.framework.annotation.GPRequestParam;
import com.gupaoedu.mvc.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@GPController
@GPRequestMapping("gpmvc")
public class DemoController {

    @GPAutowired
    private IDemoService iDemoService;

    @GPRequestMapping("hello")
    public String hello(@GPRequestParam("name") String name, HttpServletRequest request, HttpServletResponse response) {
        return iDemoService.hello(name);
    }


    @GPRequestMapping("add")
    public int add(@GPRequestParam("a") int a, @GPRequestParam("b") int b, HttpServletRequest request, HttpServletResponse response) {
        return iDemoService.add(a, b);

    }


}
