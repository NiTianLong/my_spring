package com.longye.spring.view;

import java.util.Map;

/**
 * Created by tianl on 2018/10/19.
 */
public class MyModelAndView {

    private String viewName;

    private Map<String, Object> model;

    public MyModelAndView(String viewName, Map<String, Object> model) {
        this.viewName = viewName;
        this.model = model;
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public Map<String, Object> getModel() {
        return model;
    }

    public void setModel(Map<String, Object> model) {
        this.model = model;
    }
}
