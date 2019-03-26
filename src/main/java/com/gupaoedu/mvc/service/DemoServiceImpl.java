package com.gupaoedu.mvc.service;


import com.gupaoedu.framework.annotation.GPService;

@GPService
public class DemoServiceImpl implements IDemoService {
    @Override
    public String hello(String name) {
        return "Hello " + name;
    }

    @Override
    public int add(int a, int b) {
        return a + b;
    }
}
