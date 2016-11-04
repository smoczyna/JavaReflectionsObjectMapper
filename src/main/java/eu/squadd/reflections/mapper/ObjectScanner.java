/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.squadd.reflections.mapper;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

/**
 *
 * @author z094
 */
public class ObjectScanner {

    public static String scan(Object o) {
        StringBuilder buffer = new StringBuilder();
        Class oClass = o.getClass();
        if (oClass.isArray()) {
            buffer.append("Array: ");
            buffer.append("[");
            for (int i = 0; i < Array.getLength(o); i++) {
                Object value = Array.get(o, i);
                if (value.getClass().isPrimitive()
                        || value.getClass() == java.lang.Long.class
                        || value.getClass() == java.lang.Integer.class
                        || value.getClass() == java.lang.Boolean.class
                        || value.getClass() == java.lang.String.class
                        || value.getClass() == java.lang.Double.class
                        || value.getClass() == java.lang.Short.class
                        || value.getClass() == java.lang.Byte.class) {
                    buffer.append(value);
                    if (i != (Array.getLength(o) - 1)) {
                        buffer.append(",");
                    }
                } else {
                    buffer.append(scan(value));
                }
            }
            buffer.append("]\n");
        } else {
            buffer.append("Class: ").append(oClass.getName());
            buffer.append("{\n");
            while (oClass != null) {
                Field[] fields = oClass.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    buffer.append(field.getName());
                    buffer.append("=");
                    try {
                        Object value = field.get(o);
                        if (value != null) {
                            if (value.getClass().isPrimitive()
                                    || value.getClass() == java.lang.Long.class
                                    || value.getClass() == java.lang.String.class
                                    || value.getClass() == java.lang.Integer.class
                                    || value.getClass() == java.lang.Boolean.class
                                    || value.getClass() == java.lang.Double.class
                                    || value.getClass() == java.lang.Short.class
                                    || value.getClass() == java.lang.Byte.class) {
                                buffer.append(value);
                            } else {
                                buffer.append(scan(value));
                            }
                        }
                    } catch (IllegalAccessException e) {
                        buffer.append(e.getMessage());
                    }
                    buffer.append("\n");
                }
                oClass = oClass.getSuperclass();
            }
            buffer.append("}\n");
        }
        return buffer.toString();
    }
}
