package be.florien.joinorm.annotation;

import be.florien.joinorm.architecture.DBTable;

/**
 * Created by FlamentF on 13-09-16.
 */

public @interface JoField {

    Class<? extends DBTable> getTableClass() default DBTable.class;
}
