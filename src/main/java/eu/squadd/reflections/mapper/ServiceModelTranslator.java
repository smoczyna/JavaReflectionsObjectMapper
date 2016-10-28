/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.squadd.reflections.mapper;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import org.apache.commons.beanutils.ConstructorUtils;

/**
 *
 * @author z094
 */
public class ServiceModelTranslator {
    
    /**
     * JAXB object translation methods      
     */
    
    public static Object transformXmlObj2XmlObj(Class sourceClass, Class destClass, Object source) {
        try {            
            JAXBContext sourceContext = JAXBContext.newInstance(sourceClass);
            JAXBSource jaxbSource = new JAXBSource(sourceContext, source);
            JAXBContext destContext = JAXBContext.newInstance(destClass);
            Unmarshaller unmarshaller = destContext.createUnmarshaller();
            return unmarshaller.unmarshal(jaxbSource);
        } catch (JAXBException ex) {            
            System.err.println(ex.getMessage());
            return null;
        }
    }
    
    public static String marshallToString(Class sourceClass, Object source) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(sourceClass);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter sw = new StringWriter();
            marshaller.marshal(source, sw);
            return sw.toString();            
        } catch (JAXBException ex) {            
            System.err.println(ex.getMessage());
            return null;
        }
    }
    
    public static Object unmarshallFromString(Class destClass, String xmlStr) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(destClass);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            StringReader reader = new StringReader(xmlStr);
            return unmarshaller.unmarshal(reader);            
        } catch (JAXBException ex) {
            System.err.println(ex.getMessage());
            return null;
        }
    }
    
    /**
     * Java reflections method of object translation
     * this method always works (unlike JAXB) but it doesn't support complex collection types like the one below
     * example of unsupported type: Map<String, Map<Integer, SomeBespokeType>>
     */
        
    private static <T> List<T> createListOfType(Class<T> type) {
        return new ArrayList<T>();
    }
    
    private static<T> Set<T> createSetOfType(Class<T> type) {
        return new HashSet<T>();
    }
    
    private static<C, T> Map<C, T> createMapOfTypes(Class<C> type1, Class<T> type2) {
        return new HashMap<C, T>();
    } 
    
    /**
     * this is Java Reflections translation method doing the magic where JAXB cannot
     * it is type independent as long as it is a valid java object
     * it cannot be an interface, abstract class and cannot use generics     
     * 
     * @param <T>
     * @param sourceClass   - type of the source object
     * @param destClass     - type of destination object
     * @param source        - source object itself
     * @return              - translated destination object
     */
    public static <T> T transposeModel(Class sourceClass, Class<T> destClass, Object source) {        
        Object destInstance = null;
        try {
            destInstance = ConstructorUtils.invokeConstructor(destClass, null);        
            BeanInfo destInfo = Introspector.getBeanInfo(destClass);
            PropertyDescriptor[] destProps = destInfo.getPropertyDescriptors();

            BeanInfo sourceInfo = Introspector.getBeanInfo(sourceClass, Object.class);
            PropertyDescriptor[] sourceProps = sourceInfo.getPropertyDescriptors();
            
            for (PropertyDescriptor sourceProp : sourceProps) {
                String name = sourceProp.getName();
                Method getter = sourceProp.getReadMethod();
                Class<?> sType;
                try {
                    sType = sourceProp.getPropertyType();
                } catch(NullPointerException ex) {
                    System.err.println("The type of source field cannot be determined, field skipped, name: "+name);
                    continue;
                }
                Object sValue;
                try {
                    sValue = getter.invoke(source);
                } catch (NullPointerException ex) {
                    System.err.println("Cannot obtain the value from field, field skipped, name: "+name);
                    continue;
                }                
                for (PropertyDescriptor destProp : destProps) {
                    if (destProp.getName().equals(name)) {
                        if (assignPropertyValue(sourceProp, sValue, destProp, destInstance))
                            System.out.println("Destination property assigned, source name: "+name+", type: "+sType);
                        else
                            System.err.println("Failed to assign property, source name: "+name+", type: "+sType);
                        
                        break;
                    }                    
                }
            }        
        } catch (InvocationTargetException | IntrospectionException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | InstantiationException ex) {
            Logger.getLogger(ServiceModelTranslator.class.getName()).log(Level.SEVERE, null, ex);            
        }
        return destClass.cast(destInstance);
    }

    private static boolean assignPropertyValue(PropertyDescriptor sourceProp, Object sourceValue, PropertyDescriptor destProp, Object destInstance) {
        if (sourceValue==null) {
            System.out.println("Null value found, assignment skipped");
            return true;
        }      
        boolean result = false;
        Class<?> sourceType = sourceProp.getPropertyType();
        Method getter = sourceProp.getReadMethod();        
        Class<?> destType = destProp.getPropertyType();
        Method setter = destProp.getWriteMethod();
        try {
            if (destType.isInterface() || destType.isArray() || destType.isEnum()) {
                if (Collection.class.isAssignableFrom(sourceType) && Collection.class.isAssignableFrom(destType))
                    assignCollectionValue(getter, sourceValue, destType, setter, destInstance);
                else if (Map.class.isAssignableFrom(sourceType) && Map.class.isAssignableFrom(destType))                    
                    assignMapValue(getter, sourceValue, setter, destInstance);
                else                    
                    assignMixedTypesValue(sourceType, getter, sourceValue, destType, setter, destInstance);
            }            
            else if (destType.isInstance(sourceValue) || destType.isPrimitive())
                setter.invoke(destInstance, sourceValue);
            
            else if (destType.isMemberClass())
                setter.invoke(destInstance, sourceValue);
            
            else if (destType.isAssignableFrom(sourceType))
                setter.invoke(destInstance, destType.cast(sourceValue));
                            
            else { //if (ClassUtils.isInnerClass(destType)) {
                Object member = transposeModel(sourceType, destType, sourceValue);
                member = destType.cast(member);
                setter.invoke(destInstance, member);
            }
            result = true;
        } catch (IllegalArgumentException ex) {                
            System.out.println("Looks like type mismatch, source type: "+sourceType+", dest type: "+destType);
            Logger.getLogger(ServiceModelTranslator.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            Logger.getLogger(ServiceModelTranslator.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }
    
    private static Class detectSourceCollectionPayload(Method getter) {        
        Class sourceArgClass = null;
        Type sourceArg = getter.getGenericReturnType();
        if(sourceArg instanceof ParameterizedType) {            
            ParameterizedType type = (ParameterizedType) sourceArg;
            Type[] typeArguments = type.getActualTypeArguments();
            sourceArgClass = (Class) typeArguments[0];            
        }
        return sourceArgClass;
    }
    
    private static Class detectDestCollectionPayload(Method setter) {
        Class destArgClass = null;
        Type[] genericParameterTypes = setter.getGenericParameterTypes();
        Type genericParameterType = genericParameterTypes[0];
        if (genericParameterType instanceof ParameterizedType) {
            ParameterizedType aType = (ParameterizedType) genericParameterType;
            Type[] destArgs = aType.getActualTypeArguments();
            if (destArgs.length!=1) {            
                System.out.println("3: Cannot determine payload type or multiple types found !!!");
                return null;
            }
            destArgClass = (Class) destArgs[0];
        }
        return destArgClass;
    }
    
    private static void assignCollectionValue(Method getter, Object sourceValue, Class destType, Method setter, Object destInstance) {        
        System.out.println("*** Collection found, resolving its payload type...");
        if (sourceValue==null || ((Collection) sourceValue).isEmpty()) return;
        
        Class sourceArgClass = detectSourceCollectionPayload(getter);
        Class destArgClass = detectDestCollectionPayload(setter);
        
        if (sourceArgClass!=null && destArgClass!=null) {
            System.out.println("Type sorted, populating values...");
            Collection sourceItems = (Collection) sourceValue;
            Iterator it = sourceItems.iterator();
            Collection destItems; // = null;
            switch (destType.getName()) {
                case "java.util.List":
                    destItems = createListOfType(destArgClass);
                    break;
                case "java.util.Set":
                    destItems = createSetOfType(destArgClass);
                    break;
                default:
                    System.out.println("4: Unrecognized collection, can't populate values");
                    return;
            }

            while (it.hasNext()) {                        
                Object element = transposeModel(sourceArgClass, destArgClass, it.next());
                destItems.add(element);
            }
            try {
                setter.invoke(destInstance, destItems);

            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(ServiceModelTranslator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }                 
        System.out.println("*** done");
    }
    
    private static Map<Integer, Class> detectSourceMapPayload(Method getter) {        
        Map<Integer, Class> result = new HashMap();          
        Type sourceArg = getter.getGenericReturnType();
        if(sourceArg instanceof ParameterizedType) {            
            ParameterizedType type = (ParameterizedType) sourceArg;
            Type[] typeArguments = type.getActualTypeArguments();
            if (typeArguments.length!=2) {
                System.err.println("Cannot determine payload type of source Map ");
                return null;
            }
            result.put(0, (Class) typeArguments[0]);
            result.put(1, (Class) typeArguments[1]);
        }        
        return result;
    }
    
    private static Map<Integer, Class> detectDestMapPayload(Method setter) {
        Map<Integer, Class> result = new HashMap();
        Type[] genericParameterTypes = setter.getGenericParameterTypes();
        Type genericParameterType = genericParameterTypes[0];            
        if (genericParameterType instanceof ParameterizedType) {
            ParameterizedType aType = (ParameterizedType) genericParameterType;
            Type[] destArgs = aType.getActualTypeArguments();
            if (destArgs.length!=2) {
                System.err.println("2: Cannot determine payload type of source Map, operation aborted !!!");
                return null;
            }
            result.put(0, (Class) destArgs[0]);
            result.put(0, (Class) destArgs[1]);
        }
        return result;
    }
    
    private static void assignMapValue(Method getter, Object sourceValue, Method setter, Object destInstance) {        
        System.out.println("*** Map found, resolving its payload types...");
        if (sourceValue==null || ((Map) sourceValue).isEmpty()) return;

        Map<Integer, Class> sourceMapTypes = detectSourceMapPayload(getter);
        if (sourceMapTypes.isEmpty() || sourceMapTypes.size()!=2) {
            System.err.println("Failed to determine source Map payload types, operation aborted !!!");
            return;
        }
        Class firstSourceArgClass = sourceMapTypes.get(0);
        Class secondSourceArgClass = sourceMapTypes.get(1);
        
        Map<Integer, Class> destMapTypes = detectDestMapPayload(setter);
        if (destMapTypes.isEmpty() || destMapTypes.size()!=2) {
            System.err.println("Failed to determine destination Map payload types, operation aborted !!!");
            return;
        }
        Class firstDestArgClass = destMapTypes.get(0);
        Class secordDestArgsClass = destMapTypes.get(1);
        
        if (!firstSourceArgClass.equals(firstDestArgClass)) {
            System.err.println("Map key types are different, can't translate values !!!");
            return;
        }

        System.out.println("*** Map types sorted, populating values...");
        Map sourceItems = (Map) sourceValue;                
        Map destItems = createMapOfTypes(firstDestArgClass, secordDestArgsClass);                
        for (Object key : sourceItems.entrySet()) {
            Entry entry = (Entry) destItems.get(key);
            Object destValue = transposeModel(secondSourceArgClass, secordDestArgsClass, entry.getValue());                    
            destItems.put(entry.getKey(), destValue);                    
        }
        try {
            setter.invoke(destInstance, destItems);

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(ServiceModelTranslator.class.getName()).log(Level.SEVERE, null, ex);
        }        
        System.out.println("*** done");
    }
    
    private static void assignMixedTypesValue(Class sourceType, Method getter, Object sourceValue, Class destType, Method setter, Object destInstance) {
        System.out.println("Source collection doesn't match the one on destination side, resolve their types...");
        if (sourceValue==null || destInstance==null) return;        
        if (Collection.class.isAssignableFrom(sourceType)) {
            if (((Collection) sourceValue).isEmpty()) return;
            
            Class sourceArgClass = detectSourceCollectionPayload(getter);
            if (sourceArgClass==null) {
                System.err.println("Failed to determine source Collection payload type, operation aborted !!!");
                return;
            }
            // if source is a collection then destination must be a Map
            Map<Integer, Class> destMapTypes = detectDestMapPayload(setter);
            if (destMapTypes.isEmpty() || destMapTypes.size()!=2) {
                System.err.println("Failed to determine destination Map payload types, operation aborted !!!");
                return;
            }
            Class firstDestArgClass = destMapTypes.get(0);
            Class secordDestArgsClass = destMapTypes.get(1);
            
            System.out.println("*** Both Collection types sorted, populating values...");
            Map destItems = createMapOfTypes(firstDestArgClass, secordDestArgsClass);                
            //for (Object key : sourceItems.entrySet()) {
            Collection sourceItems = (Collection) sourceValue;
            Iterator it = sourceItems.iterator();
            Integer i=0;
            while (it.hasNext()) {                
                Object element = transposeModel(sourceArgClass, secordDestArgsClass, it.next());
                destItems.put(++i, element);
            }
            try {
                setter.invoke(destInstance, destItems);

            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(ServiceModelTranslator.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("*** done");            
        }
        else if (Map.class.isAssignableFrom(sourceType)) {
            if (((Map) sourceValue).isEmpty()) return;
            
            Map<Integer, Class> sourceMapTypes = detectSourceMapPayload(getter);
            if (sourceMapTypes.isEmpty() || sourceMapTypes.size()!=2) {
                System.err.println("Failed to determine source Map payload types, operation aborted !!!");
                return;
            }
            //Class firstSourceArgClass = sourceMapTypes.get(0); // dummy, not used anywere
            Class secondSourceArgClass = sourceMapTypes.get(1);
            Map sourceItems = (Map) sourceValue;
            
            // if source is a Map then destination must be a Collection
            Class destArgClass = detectDestCollectionPayload(setter);
            if (destArgClass==null) {
                System.err.println("Failed to determine destination Collection payload type, operation aborted !!!");
                return;
            }
            Collection destItems;
            switch (destType.getName()) {
                case "java.util.List":
                    destItems = createListOfType(destArgClass);
                    break;
                case "java.util.Set":
                    destItems = createSetOfType(destArgClass);
                    break;
                default:
                    System.out.println("4: Unrecognized collection, can't populate values");
                    return;
            }
            for (Object value : sourceItems.values()) {
                Object destValue = transposeModel(secondSourceArgClass, destArgClass, value);
                destItems.add(destValue);
            }
            try {
                setter.invoke(destInstance, destItems);

            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(ServiceModelTranslator.class.getName()).log(Level.SEVERE, null, ex);
            }        
            System.out.println("*** done");            
        }
        else {
            System.out.println("4: Unrecognized collection type, cannot proceed, type: " + sourceType.getName());
        }
    }    
}
