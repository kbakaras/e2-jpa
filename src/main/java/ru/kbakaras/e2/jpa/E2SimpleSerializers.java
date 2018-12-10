package ru.kbakaras.e2.jpa;

import javax.persistence.metamodel.Attribute;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class E2SimpleSerializers {
    private Map<Class, Function> map = new HashMap<>();

    public <F> E2SimpleSerializers register(Class<F> clazz, Function<F, String> func) {
        map.put(clazz, func);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <F> E2SimpleSerializers registerNullable(Class<F> clazz, Function<F, String> func) {
        map.put(clazz, value -> Optional.ofNullable((F) value).map(func).orElse(null));
        return this;
    }

    public boolean contains(Class clazz) {
        return map.containsKey(clazz);
    }

    public String toString(Attribute attr, Object element) {
        return toString(attr.getJavaType(), attributeValue(attr, element));
    }

    public String toString(Class valueType, Object value) {
        @SuppressWarnings("unchecked")
        Function<Object, String> ss = map.get(valueType);
        if (ss != null) {
            return ss.apply(value);
        } else {
            throw new E2SerializationException("Unable to locate simple serializer for type " + valueType);
        }
    }

    private static Object attributeValue(Attribute attr, Object element) {
        try {
            return ((Field) attr.getJavaMember()).get(element);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}