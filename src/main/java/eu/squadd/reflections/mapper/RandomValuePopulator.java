/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.squadd.reflections.mapper;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.beanutils.ConstructorUtils;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

/**
 *
 * @author z094
 */
public class RandomValuePopulator {

    private final PodamFactory podamFactory = new PodamFactoryImpl();

    private <P> P getManufacturedPojo(final Class<P> klass) {
        if (klass.getEnclosingClass()!=null && klass.getEnclosingClass().equals(Number.class)) {
            
            try {
                return klass.newInstance();
            } catch (InstantiationException | IllegalAccessException ex) {
                Logger.getLogger(RandomValuePopulator.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        } else 
            return podamFactory.manufacturePojo(klass);
    }

    private <P> P instanciateMathNumber(final Class<P> klass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return (P) ConstructorUtils.invokeConstructor(klass, 0);
//        try {
//            
//        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException ex) {
//            Logger.getLogger(RandomValuePopulator.class.getName()).log(Level.SEVERE, null, ex);
//            throw ex;
//        }
    }
    
    private Set<Field> getAllFields(Class targetClass, Predicate<Field> alwaysTrue) {
        Field[] fields = targetClass.getDeclaredFields();
        Set<Field> result = new HashSet();
        result.addAll(Arrays.asList(fields));
        return result;
    }
    
    public Object populateAllFields(final Class targetClass) throws IllegalAccessException, InstantiationException {        
        final Object target;
        try {
            if (isMathNumberType(targetClass)) {
                System.out.println("Math Number found !!!");
                target = ConstructorUtils.invokeConstructor(targetClass, 0L);
            }    
            else
                target = ConstructorUtils.invokeConstructor(targetClass, null);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            return null;
        }

        //Get all fields present on the target class
        final Set<Field> allFields = getAllFields(targetClass, Predicates.<Field>alwaysTrue());

        //Iterate through fields
        for (final Field field : allFields) {

            //Set fields to be accessible even when private
            field.setAccessible(true);

            final Class<?> fieldType = field.getType();
            if (fieldType.isEnum() && Enum.class.isAssignableFrom(fieldType)) {
                //handle any enums here if you have any
            }

            //Check if the field is a collection
            if (Collection.class.isAssignableFrom(fieldType)) {

                //Get the generic type class of the collection
                final Class<?> genericClass = getGenericClass(field);

                //Check if the generic type of a list is abstract
                if (Modifier.isAbstract(genericClass.getModifiers())) {

                    System.out.println("Abstract classes are not supported !!!");
                    
                    //You might want to use any class that extends
                    //Your abstract class like 
                    
                    // this stuff needs real class extending abstract one to work
                    //final List<Object> list = new ArrayList();
                    //list.add(populateAllIn(ClassExtendingAbstract.class));
                    //field.set(target, list);
                    

                } else {
                    final List<Object> list = new ArrayList();
                    list.add(populateAllFields(genericClass));
                    field.set(target, list);
                }

            } else if ((isSimpleType(fieldType) || isSimplePrimitiveWrapperType(fieldType)) && !fieldType.isEnum()) {
                field.set(target, getManufacturedPojo(fieldType));

            } else if (!fieldType.isEnum()) {
                field.set(target, populateAllFields(fieldType));
            }
        }
        return target;
    }

    private Class<?> getGenericClass(final Field field) {
        final ParameterizedType collectionType = (ParameterizedType) field.getGenericType();
        return (Class<?>) collectionType.getActualTypeArguments()[0];
    }

    private boolean isSimpleType(final Class<?> fieldType) {
        return fieldType.isPrimitive()
                || fieldType.isEnum()
                || String.class.isAssignableFrom(fieldType)
                || Date.class.isAssignableFrom(fieldType);
    }

    private boolean isSimplePrimitiveWrapperType(final Class<?> fieldType) {
        return Integer.class.isAssignableFrom(fieldType)
                || Boolean.class.isAssignableFrom(fieldType)
                || Character.class.isAssignableFrom(fieldType)
                || Long.class.isAssignableFrom(fieldType)
                || Short.class.isAssignableFrom(fieldType)
                || Double.class.isAssignableFrom(fieldType)
                || Float.class.isAssignableFrom(fieldType)
                || Byte.class.isAssignableFrom(fieldType);
                //|| BigDecimal.class.isAssignableFrom(fieldType)
                //|| BigInteger.class.isAssignableFrom(fieldType);
    }

    private boolean isMathNumberType(final Class<?> fieldType) {
        //return ((fieldType.getClass().getEnclosingClass()!=null && fieldType.getClass().getEnclosingClass().equals(Number.class)));                
        return fieldType.getClass().equals(BigDecimal.class) || fieldType.getClass().equals(BigInteger.class);
    }
    
//    public static void randomlyPopulateFields(Object object) {
//        new RandomValueFieldPopulator().populate(object);
//    }
//
//    public static class RandomValueFieldPopulator {
//
//        public void populate(Object object) {
//            ReflectionUtils.doWithFields(object.getClass(), new RandomValueFieldSetterCallback(object));
//        }
//
//        private static class RandomValueFieldSetterCallback implements FieldCallback {
//
//            private Object targetObject;
//
//            public RandomValueFieldSetterCallback(Object targetObject) {
//                this.targetObject = targetObject;
//            }
//
//            @Override
//            public void doWith(Field field) throws IllegalAccessException {
//                Class<?> fieldType = field.getType();
//                if (!Modifier.isFinal(field.getModifiers())) {
//                    Object value = generateRandomValue(fieldType, new WarnOnCantGenerateValueHandler(field));
//                    if (!value.equals(UNGENERATED_VALUE_MARKER)) {
//                        ReflectionUtils.makeAccessible(field);
//                        field.set(targetObject, value);
//                    }
//                }
//            }
//        }
//    }
//
//    public static Object generateRandomValue(Class<?> fieldType, CantGenerateValueHandler cantGenerateValueHandler) {
//        if (fieldType.equals(String.class)) {
//            return UUID.randomUUID().toString();
//        } else if (Date.class.isAssignableFrom(fieldType)) {
//            return new Date(System.currentTimeMillis() - random.nextInt(DATE_WINDOW_MILLIS));
//        } else if (Number.class.isAssignableFrom(fieldType)) {
//            return random.nextInt(Byte.MAX_VALUE) + 1;
//        } else if (fieldType.equals(Integer.TYPE)) {
//            return random.nextInt();
//        } else if (fieldType.equals(Long.TYPE)) {
//            return random.nextInt();
//        } else if (Enum.class.isAssignableFrom(fieldType)) {
//            Object[] enumValues = fieldType.getEnumConstants();
//            return enumValues[random.nextInt(enumValues.length)];
//        } else {
//            return cantGenerateValueHandler.handle();
//        }
//    }
}
