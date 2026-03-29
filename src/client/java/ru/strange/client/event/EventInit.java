package ru.strange.client.event;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventInit {

    byte value() default Priority.MEDIUM;
}
