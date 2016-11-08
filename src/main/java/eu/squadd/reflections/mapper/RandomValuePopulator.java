/*
 *
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
import eu.squadd.testing.objectspopulator.api.ScannerFactoryImpl;
import eu.squadd.testing.objectspopulator.api.ScannerFactory;

/**
 *
 * @author z094
 */
public class RandomValuePopulator {

    private final ScannerFactory podamFactory = new ScannerFactoryImpl();

    private <P> P getManufacturedPojo(final Class<P> klass) {
        return podamFactory.manufacturePojo(klass);
    }
    
    private <P> P getMathNumberType(final Class<P> klass) {
        try {
            if (BigDecimal.class.isAssignableFrom(klass))
                return (P) ConstructorUtils.invokeConstructor(klass, 0L);
            else if (BigInteger.class.isAssignableFrom(klass))
                return (P) ConstructorUtils.invokeConstructor(klass, 0);
            else {
                System.out.println("*** Unknown Math type, skipping for now !!!");
                return null;
            }                
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException ex) {
            Logger.getLogger(RandomValuePopulator.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
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
                target = getMathNumberType(targetClass);
            }    
            else
                target = ConstructorUtils.invokeConstructor(targetClass, null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException ex) {
            System.err.println(ex.getMessage());
            return null;
        }

        //final Object target = targetClass.newInstance();

        //Get all fields present on the target class
        final Set<Field> allFields = getAllFields(targetClass, Predicates.<Field>alwaysTrue());

        //Iterate through fields
        for (final Field field : allFields) {

            //Set fields to be accessible even when private
            field.setAccessible(true);

            final Class<?> fieldType = field.getType();
            
            if (fieldType.isEnum() && Enum.class.isAssignableFrom(fieldType)) {
                //handle any enums here if you have any
            
            } else if (isMathNumberType(fieldType)) {                
                //System.out.println("*** Math number found, populating it: "+fieldType);                
                field.set(target, getManufacturedPojo(fieldType));
            }            
            
            //Check if the field is a collection
            else if (Collection.class.isAssignableFrom(fieldType)) {

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
    }

    private boolean isMathNumberType(final Class<?> fieldType) {
        //return ((fieldType.getClass().getEnclosingClass()!=null && fieldType.getClass().getEnclosingClass().equals(Number.class)));                
        //return fieldType.getClass().equals(BigDecimal.class) || fieldType.getClass().equals(BigInteger.class);
        return Number.class.isAssignableFrom(fieldType);
    }

}
