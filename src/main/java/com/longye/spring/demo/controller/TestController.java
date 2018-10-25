package com.longye.spring.demo.controller;

import com.longye.spring.demo.service.AppleService;
import com.longye.spring.demo.service.OrangeService;
import com.longye.spring.framework.annotation.MyAutowired;
import com.longye.spring.framework.annotation.MyController;
import com.longye.spring.framework.annotation.MyRequestMapping;
import com.longye.spring.framework.annotation.MyRequestParam;
import com.longye.spring.view.MyModelAndView;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by tianl on 2018/10/17.
 */
@MyController
@MyRequestMapping("/test")
public class TestController {

    @MyAutowired
    private AppleService appleService;

    @MyAutowired("orange")
    private OrangeService orangeService;

    /**
     * 为了方便获取参数名,Controller中的方法的参数前的都添加上@MyRequestParam注解,且注解中都定义了参数名
     * @param name
     * @param count
     */
    @MyRequestMapping(value ="/fruit")
    public MyModelAndView queryFruit(@MyRequestParam(value = "name",required = true) String name,
                                     @MyRequestParam(value = "count",required = true)String count){

        Map<String, Object> model = new HashMap<>();
        model.put("name", name);
        model.put("count", count);

        return new MyModelAndView("fruit.jspk", model);
    }
}
