package be.florien.joinorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import be.florien.joinorm.architecture.DBTable;

/**
 * Created by FlamentF on 19-09-16.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = ElementType.METHOD)
public @interface JoCustomJoin {

    String getParams();
    Class<? extends DBTable> getTableFor() default DBTable.class;
}
