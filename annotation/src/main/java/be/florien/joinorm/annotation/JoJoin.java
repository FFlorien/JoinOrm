package be.florien.joinorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import be.florien.joinorm.architecture.DBTable;

/**
 * Created by FlamentF on 13-09-16.
 */

@Target(value = ElementType.FIELD)
public @interface JoJoin {

    String IGNORE = "#ignore";

    Class<? extends DBTable> getTableClass() default DBTable.class;
    boolean isReferenceJoin() default false;
    boolean isLeftJoin() default false;
    String getTableRef() default IGNORE; //todo tableref, alias, fieldName : how to handle ?
    String getAlias() default IGNORE;

}
