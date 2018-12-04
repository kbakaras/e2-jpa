package ru.kbakaras.e2.jpa;

import javax.annotation.Resource;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class E2Metamodel {
    @Resource private E2SimpleSerializers simpleSerializers;

    private static class EntityTreat {
        public final Function<Object, UUID> uidGetter;
        public boolean synth;

        public EntityTreat(Function<Object, UUID> uidGetter, boolean synth) {
            this.uidGetter = uidGetter;
            this.synth = synth;
        }
    }

    private Metamodel metamodel;
    private Map<Class, EntityTreat> treats = new HashMap<>();

    public E2Metamodel(EntityManagerFactory emf) {
        this.metamodel = emf.getMetamodel();
    }

    public ElementReader read(Object element) {
        return new ElementReader(element);
    }


    public class ElementReader implements Iterable<ElementReader.AttributeReader> {
        private Object element;
        private EntityType entity;

        private boolean synth;
        private UUID uid;

        private ElementReader(Object element) {
            this.element = element;
            this.entity = metamodel.entity(element.getClass());

            EntityTreat treat = treat(element.getClass());
            this.synth = treat.synth;
            this.uid = treat.uidGetter.apply(element);

        }

        public String entityName() {
            return element.getClass().getName();
        }

        public boolean synth() {
            return synth;
        }

        public UUID uid() {
            return uid;
        }

        @Override
        public Iterator<AttributeReader> iterator() {
            return new Iterator<AttributeReader>() {
                @SuppressWarnings("unchecked")
                private Iterator<Attribute> iter = entity.getAttributes().iterator();

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public AttributeReader next() {
                    return new AttributeReader(iter.next());
                }
            };
        }


        public class AttributeReader {
            private Attribute attribute;

            public AttributeReader(Attribute attribute) {
                this.attribute = attribute;
            }

            public boolean isAssociation() {
                return attribute.isAssociation();
            }

            public String name() {
                return attribute.getName();
            }

            public Object value() {
                try {
                    return ((Field) attribute.getJavaMember()).get(element);
                } catch (IllegalAccessException e) {
                    throw new E2SerializationException(e);
                }
            }

            public String simpleValue() {
                return simpleSerializers.toString(attribute, element);
            }
        }
    }


    /**
     * Позволяет сопоставить с классом сущности функцию, которая умеет определять uid элементов данной сущности.
     * Это нужно в тех случаях, когда uid не находится в идентификаторе (первичном ключе).<br/>
     * Если для класса сущности указано данное сопоставление, при сериализации признак synth не будет установлен.
     * @param entityClass Класс сущности
     * @param getter Функция, которая будет извлекать uid.
     * @param <T> Для удобства написания лямбд функция параметризирована.
     */
    @SuppressWarnings("unchecked")
    public <T> void registerUidGetter(Class<T> entityClass, Function<T, UUID> getter) {
        treats.put(entityClass, new EntityTreat((Function<Object, UUID>) getter, false));
    }


    private EntityTreat treat(Class entityClass) {
        EntityTreat treat = treats.get(entityClass);
        if (treat == null) {
            EntityType entity = metamodel.entity(entityClass);
            boolean synth = !UUID.class.equals(entity.getIdType().getJavaType());
            if (!synth) {
                treat = new EntityTreat(element -> {
                    try {
                        return (UUID) ((Field) entity.getId(UUID.class).getJavaMember()).get(element);
                    } catch (IllegalAccessException e) {
                        throw new E2SerializationException(e);
                    }
                }, synth);
            } else {
                treat = SYNTH;
            }
            treats.put(entityClass, treat);
        }
        return treat;
    }

    private static EntityTreat SYNTH = new EntityTreat(element -> UUID.randomUUID(), true);
}