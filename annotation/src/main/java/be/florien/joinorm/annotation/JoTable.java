package be.florien.joinorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * todo
 */

@Target(value = ElementType.TYPE)
public @interface JoTable {

    String STRING_IGNORE = "#ignore";

    boolean isGeneratingWrite() default true;
    boolean isGeneratingSelect() default true;
    String tableName() default STRING_IGNORE;
}
