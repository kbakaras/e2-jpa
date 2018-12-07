package ru.kbakaras.e2.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kbakaras.e2.jpa.E2Metamodel.ElementReader;
import ru.kbakaras.e2.jpa.E2Metamodel.ElementReader.AttributeReader;
import ru.kbakaras.e2.message.E2Element;
import ru.kbakaras.e2.message.E2Payload;
import ru.kbakaras.e2.message.E2Reference;
import ru.kbakaras.e2.message.E2Table;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class E2Serializer {
    private static final Logger LOG = LoggerFactory.getLogger(E2Serializer.class);

    private E2Metamodel metamodel;
    private E2Payload payload;

    private Map<Object, E2Reference> serialized = new HashMap<>();

    public E2Serializer(E2Metamodel metamodel, E2Payload payload) {
        this.metamodel = metamodel;
        this.payload = payload;
    }

    public E2Reference serialize(Object element, boolean changed) {
        E2Reference reference = serialized.get(element);

        if (reference == null) {
            LOG.debug("Serializing ({}) {}", element.getClass(), element);
            ElementReader reader = metamodel.read(element);

            E2Element e2element = payload.createEntity(reader.entityName())
                    .addElement(reader.uid().toString())
                    .setChanged(changed)
                    .setSynth(reader.synth());

            reference = e2element.asReference();
            serialized.put(element, reference);

            for (AttributeReader attributeReader: reader) {
                if (!attributeReader.isAssociation()) {
                    String value = attributeReader.simpleValue();
                    if (value != null) {
                        e2element.attributes.add(attributeReader.name()).setValue(value);
                    }

                } else {
                    if (!attributeReader.isCollection()) {
                        Object value = attributeReader.value();
                        if (value != null) {
                            serialize(attributeReader.value(), false)
                                    .apply(e2element.attributes.add(attributeReader.name()));
                        }

                    } else {
                        Collection collection = attributeReader.collection();
                        if (collection != null && !collection.isEmpty()) {
                            E2Table table = e2element.addTable(attributeReader.name());

                            for (Object item: collection) {
                                serialize(item, false)
                                        .apply(table.addRow().attributes.add("item"));
                            }
                        }
                    }
                }
            }

        } else if (changed) {
            payload.referencedElement(reference)
                    .ifPresent(e2Element -> e2Element.setChanged(true));
        }

        return reference;
   }
}