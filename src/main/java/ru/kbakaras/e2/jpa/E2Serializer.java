package ru.kbakaras.e2.jpa;

import ru.kbakaras.e2.jpa.E2Metamodel.ElementReader;
import ru.kbakaras.e2.jpa.E2Metamodel.ElementReader.AttributeReader;
import ru.kbakaras.e2.message.E2Element;
import ru.kbakaras.e2.message.E2Payload;
import ru.kbakaras.e2.message.E2Reference;

import java.util.HashMap;
import java.util.Map;

public class E2Serializer {
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
            ElementReader reader = metamodel.read(element);

            E2Element e2element = payload.createEntity(reader.entityName())
                    .addElement(reader.uid().toString())
                    .setChanged(changed)
                    .setSynth(reader.synth());

            reference = e2element.asReference();
            serialized.put(element, reference);

            for (AttributeReader attributeReader: reader) {
                if (attributeReader.isAssociation()) {
                    Object value = attributeReader.value();
                    if (value != null) {
                        serialize(attributeReader.value(), false)
                                .apply(e2element.attributes.add(attributeReader.name()));
                    }
                } else {
                    String value = attributeReader.simpleValue();
                    if (value != null) {
                        e2element.attributes.add(attributeReader.name()).setValue(value);
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