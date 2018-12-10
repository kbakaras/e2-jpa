package ru.kbakaras.e2.jpa;

import ru.kbakaras.e2.jpa.E2Metamodel.ElementWriter;
import ru.kbakaras.e2.jpa.E2Metamodel.ElementWriter.AttributeWriter;
import ru.kbakaras.e2.message.E2Attribute;
import ru.kbakaras.e2.message.E2Attributes;
import ru.kbakaras.e2.message.E2Element;
import ru.kbakaras.e2.message.E2Entity;
import ru.kbakaras.e2.message.E2Payload;
import ru.kbakaras.e2.message.E2Row;
import ru.kbakaras.e2.message.E2Table;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public void deserializeAll() {
        for (E2Entity entity : payload.entities()) {
            for (E2Element element : entity.elementsChanged()) {
                deserializeElement(element);
            }
        }
    }

    public Object deserializeElement(E2Element element) {
        Object itemInstance = instances.get(element);
        if (itemInstance == null && !element.isDeleted()) {
            EntityInstance instanceObject = findEntity(element);
            itemInstance = instanceObject.getInstance();

            E2Attributes attributes = element.attributes;
            instances.put(element, itemInstance);

            ElementWriter writer = metamodel.write(itemInstance);
            for (AttributeWriter attributeWriter : writer) {
                if (attributeWriter.isAssociation()) {
                    attributes
                            .get(attributeWriter.name())
                            .map(E2Attribute::reference)
                            .map(e2Reference -> payload.referencedElement(e2Reference).orElse(null))
                            .map(this::deserializeElement)
                            .ifPresent(attributeWriter::setValue);
                } else if (attributeWriter.isCollection()) {
                    element.table(attributeWriter.name())
                            .map(this::deserializeCollection)
                            .ifPresent(attributeWriter::setValue);
                } else {
                    attributes
                            .get(attributeWriter.name())
                            .map(e2Attribute -> e2Attribute.value().string())
                            .ifPresent(attributeWriter::setSimpleValue);
                }
            }
            writeElement(itemInstance, instanceObject.isNew());
        }
        return itemInstance;
    }

    private List<Object> deserializeCollection(E2Table table) {
        List<Object> collection = new ArrayList<>();
        for (E2Row e2Row : table) {
            e2Row.attributes.get("item")
                    .map(attribute -> payload.referencedElement(attribute.reference()).orElse(null))
                    .map(this::deserializeElement)
                    .ifPresent(collection::add);
        }

        return collection;
    }

    private EntityInstance findEntity(E2Element element) {
        try {
            boolean isNew = false;
            Object instance = entityManager.find(Class.forName(element.entityName()), getElementId(element));
            if (instance == null) {
                instance = createInstance(element.entityName());
                isNew = true;
            }
            return new EntityInstance(isNew, instance);
        } catch (ClassNotFoundException e) {
            throw new E2DeserializationException(e);
        }
    }

    private void writeElement(Object instance, boolean isNew) {
        if (isNew) {
            entityManager.persist(instance);
        } else {
            entityManager.merge(instance);
        }
    }

    private Object getElementId(E2Element element) {
        String uid = element.getUid();
        try {
            E2Attributes attributes = element.attributes;
            EntityType type = metamodel.getEntityType(Class.forName(element.entityName()));

            if (element.isSynth()) {
                String value = attributes
                        .get("id")
                        .map(attribute -> attribute.value().string())
                        .orElse(null);
                if (value != null) {
                    return simpleDeserializers.attributeValue(type.getIdType().getJavaType(), value);
                }
            }
            return UUID.fromString(uid);
        } catch (ReflectiveOperationException e) {
            throw new E2DeserializationException(e);
        }
    }

    private Object createInstance(String className) {
        try {
            Class<?> itemClass = Class.forName(className);
            Constructor<?> constructor = itemClass.getDeclaredConstructor();
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new E2DeserializationException(e);
        }
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