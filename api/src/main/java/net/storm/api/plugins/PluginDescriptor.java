package net.storm.api.plugins;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginDescriptor {
    String name();
    String description() default "";
    boolean enabledByDefault() default true;
}
