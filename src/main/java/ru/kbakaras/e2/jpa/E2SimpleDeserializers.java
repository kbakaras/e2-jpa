package ru.kbakaras.e2.jpa;

import javax.persistence.metamodel.Attribute;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * project: e2-jpa
 * author:  kostrovik
 * date:    2018-12-06
 */
public class E2SimpleDeserializers {
    private Map<Class, Function> map = new HashMap<>();

    public <F> E2SimpleDeserializers register(Class<F> clazz, Function<String, F> func) {
        map.put(clazz, func);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <F> E2SimpleDeserializers registerNullable(Class<F> clazz, Function<String, F> func) {
        map.put(clazz, value -> Optional.ofNullable((String) value).map(func).orElse(null));
        return this;
    }

    public boolean contains(Class clazz) {
        return map.containsKey(clazz);
    }

    public Object attributeValue(Attribute attr, String value) {
        @SuppressWarnings("unchecked")
        Function<String, Object> ss = map.get(attr.getJavaType());
        if (ss != null) {
            return ss.apply(value);
        } else {
            throw new E2SerializationException("Unable to locate simple deserializer for type " + attr.getJavaType());
        }
    }
}