package com.longye.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * Created by tianl on 2018/10/17.
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyRequestParam {

    String value() default "";

    boolean required() default false;
}
