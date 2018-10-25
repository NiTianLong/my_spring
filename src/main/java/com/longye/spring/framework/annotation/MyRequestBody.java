package com.longye.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * Created by tianl on 2018/10/17.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyRequestBody {

    String value() default "";
}
