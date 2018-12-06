package ru.kbakaras.e2.jpa;

import ru.kbakaras.e2.message.E2Attribute;
import ru.kbakaras.e2.message.E2Attributes;
import ru.kbakaras.e2.message.E2Element;
import ru.kbakaras.e2.message.E2Entity;
import ru.kbakaras.e2.message.E2Payload;
import ru.kbakaras.e2.message.E2Reference;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * project: e2-jpa
 * author:  kostrovik
 * date:    2018-12-06
 * github:  https://github.com/kostrovik/e2-jpa
 */
public class E2Deserializer {
    private E2Metamodel metamodel;
    private E2Payload payload;
    private Map<UUID, Object> instances = new HashMap<>();

    public E2Deserializer(E2Metamodel metamodel, E2Payload payload) {
        this.metamodel = metamodel;
        this.payload = payload;
    }

    public Map<UUID, Object> deserialize() throws ReflectiveOperationException {
        List<E2Entity> entities = payload.entities();
        for (E2Entity entity : entities) {
            List<E2Element> elements = entity.elementsChanged();
            for (E2Element element : elements) {
                deserializeElement(element);
            }
        }

        return instances;
    }

    private Object deserializeElement(E2Element element) throws ReflectiveOperationException {
        String uid = element.getUid();

        Object itemInstance = instances.get(UUID.fromString(uid));
        if (itemInstance == null) {
            E2Attributes attributes = element.attributes;
            itemInstance = createInstance(element.entityName());
            instances.put(UUID.fromString(uid), itemInstance);

            E2Metamodel.ElementWriter writer = metamodel.write(itemInstance);
            for (E2Metamodel.ElementWriter.AttributeWriter attributeWriter : writer) {
                if (attributeWriter.isAssociation()) {
                    Optional<E2Attribute> attribute = attributes.get(attributeWriter.name());
                    if (attribute.isPresent()) {
                        E2Attribute value = attribute.get();
                        E2Reference reference = value.reference();

                        Optional<E2Entity> refEntity = payload.entity(reference.entityName);
                        if (refEntity.isPresent()) {
                            E2Entity referencedEntity = refEntity.get();
                            Optional<E2Element> refElement = referencedEntity.element(reference.elementUid);

                            if (refElement.isPresent()) {
                                E2Element referencedElement = refElement.get();
                                Object instance = deserializeElement(referencedElement);
                                attributeWriter.setValue(instance);
                            }
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
        }
        return itemInstance;
    }

    private Object createInstance(String className) throws ReflectiveOperationException {
        Class<?> itemClass = Class.forName(className);
        Constructor<?> constructor = itemClass.getDeclaredConstructor();
        return constructor.newInstance();
    }
}