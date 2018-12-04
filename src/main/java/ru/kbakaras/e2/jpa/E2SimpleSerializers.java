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
        @SuppressWarnings("unchecked")
        Function<Object, String> ss = map.get(attr.getJavaType());
        if (ss != null) {
            return ss.apply(attributeValue(attr, element));
        } else {
            throw new E2SerializationException("Unable to locate simple serializer for type " + attr.getJavaType());
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