package ru.kbakaras.e2.jpa;

import ru.kbakaras.e2.jpa.E2Metamodel.ElementReader;
import ru.kbakaras.e2.jpa.E2Metamodel.ElementReader.AttributeReader;
import ru.kbakaras.e2.jpa.E2Metamodel.ElementWriter;
import ru.kbakaras.e2.jpa.E2Metamodel.ElementWriter.AttributeWriter;
import ru.kbakaras.e2.message.E2Attribute;
import ru.kbakaras.e2.message.E2Attributes;
import ru.kbakaras.e2.message.E2Element;
import ru.kbakaras.e2.message.E2Entity;
import ru.kbakaras.e2.message.E2Payload;
import ru.kbakaras.e2.message.E2Reference;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * project: e2-jpa
 * author:  kostrovik
 * date:    2018-12-06
 */
public class E2Deserializer {
    private E2Metamodel metamodel;
    private E2Payload payload;
    private Map<E2Element, Object> instances = new HashMap<>();
    private EntityManager entityManager;
    @Resource
    private E2SimpleDeserializers simpleDeserializers;

    public E2Deserializer(E2Metamodel metamodel, E2Payload payload, EntityManager entityManager) {
        this.metamodel = metamodel;
        this.payload = payload;
        this.entityManager = entityManager;
    }

    public void deserializeAll() throws ReflectiveOperationException {
        for (E2Entity entity : payload.entities()) {
            for (E2Element element : entity.elementsChanged()) {
                deserializeElement(element);
            }
        }
    }

    public Object deserializeElement(E2Element element) throws ReflectiveOperationException {
        Object itemInstance = instances.get(element);
        if (itemInstance == null && !element.isDeleted()) {
            EntityInstance instanceObject = findEntity(element);
            itemInstance = instanceObject.getInstance();

            E2Attributes attributes = element.attributes;
            instances.put(element, itemInstance);

            ElementWriter writer = metamodel.write(itemInstance);
            for (AttributeWriter attributeWriter : writer) {
                if (attributeWriter.isAssociation()) {
                    E2Reference reference = attributes.get(attributeWriter.name()).map(E2Attribute::reference).orElse(null);
                    if (reference != null) {
                        Optional<E2Element> refElement = payload.referencedElement(reference);

                        if (refElement.isPresent()) {
                            E2Element referencedElement = refElement.get();
                            Object instance = deserializeElement(referencedElement);
                            attributeWriter.setValue(instance);
                        }
                    }
                } else {
                    String value = null;
                    Optional<E2Attribute> attribute = attributes.get(attributeWriter.name());
                    if (attribute.isPresent()) {
                        value = attribute.get().value().string();
                    }

                    if (value != null) {
                        attributeWriter.setSimpleValue(value);
                    }
                }
            }
            writeElement(itemInstance, instanceObject.isNew());
        }
        return itemInstance;
    }

    private EntityInstance findEntity(E2Element element) throws ReflectiveOperationException {
        boolean isNew = false;
        Object entityId = getElementId(element);
        Object instance = entityManager.find(Class.forName(element.entityName()), entityId);
        if (instance == null) {
            instance = createInstance(element.entityName());
            isNew = true;
        }
        return new EntityInstance(isNew, instance);
    }

    private void writeElement(Object instance, boolean isNew) {
        if (isNew) {
            entityManager.persist(instance);
        } else {
            entityManager.merge(instance);
        }
    }

    private Object getElementId(E2Element element) throws ReflectiveOperationException {
        String uid = element.getUid();
        String entityName = element.entityName();
        Object result = createInstance(entityName);

        E2Attributes attributes = element.attributes;

        if (element.isSynth()) {
            String val = attributes.get("id").map(attribute -> attribute.value().string()).orElse(null);
            ElementReader reader = metamodel.read(result);
            for (AttributeReader attributeReader : reader) {
                if (attributeReader.name().equals("id")) {
                    return simpleDeserializers.attributeValue(attributeReader.attribute(), val);
                }
            }
        }
        return uid;
    }

    private Object createInstance(String className) throws ReflectiveOperationException {
        Class<?> itemClass = Class.forName(className);
        Constructor<?> constructor = itemClass.getDeclaredConstructor();
        return constructor.newInstance();
    }

    private class EntityInstance {
        private boolean isNew;
        private Object instance;

        EntityInstance(boolean isNew, Object instance) {
            this.isNew = isNew;
            this.instance = instance;
        }

        boolean isNew() {
            return isNew;
        }

        Object getInstance() {
            return instance;
        }
    }
}