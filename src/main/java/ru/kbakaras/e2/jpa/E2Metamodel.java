package ru.kbakaras.e2.jpa;

import javax.annotation.Resource;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class E2Metamodel {
    @Resource private E2SimpleSerializers simpleSerializers;

    private UUID systemUid;
    private String systemName;

    private Metamodel metamodel;
    private Map<Class, EntitySetup> setups = new HashMap<>();

    public E2Metamodel(UUID systemUid, String systemName, EntityManagerFactory emf) {
        this.systemUid  = systemUid;
        this.systemName = systemName;
        this.metamodel  = emf.getMetamodel();
    }


    public UUID getSystemUid() {
        return systemUid;
    }

    public String getSystemName() {
        return systemName;
    }


    public ElementReader read(Object element) {
        return new ElementReader(element);
    }


    public class ElementReader implements Iterable<ElementReader.AttributeReader> {
        private Object element;
        private EntityType entity;
        private EntitySetup setup;

        private boolean synth;
        private UUID uid;

        private ElementReader(Object element) {
            this.element = element;
            this.entity = metamodel.entity(element.getClass());

            setup = getSetup(element.getClass());
            this.synth = setup.synth();
            this.uid = setup.getUid(element);

            if (this.uid == null) {
                this.uid = UUID.randomUUID();
                this.synth = true;
            }
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
                private Iterator<Attribute> iterator = ((Set<Attribute>) entity.getAttributes())
                        .stream()
                        .filter(attribute -> !setup.skipped().contains(attribute.getName()))
                        .iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public AttributeReader next() {
                    return new AttributeReader(iterator.next());
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

            public boolean isCollection() {
                return attribute.isCollection();
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

            public Collection collection() {
                try {
                    return (Collection) ((Field) attribute.getJavaMember()).get(element);
                } catch (IllegalAccessException e) {
                    throw new E2SerializationException(e);
                }
            }

            public String simpleValue() {
                return simpleSerializers.toString(attribute, element);
            }
        }
    }


    public <T> EntitySetup<T> setup(Class<T> entityClass) {
        @SuppressWarnings("unchecked")
        EntitySetup<T> setup = setups.get(entityClass);
        if (setup == null) {
            setup = new EntitySetup<>();
            setups.put(entityClass, setup);
        }
        return setup;
    }


    @SuppressWarnings("unchecked")
    private EntitySetup getSetup(Class entityClass) {
        EntitySetup setup = setups.get(entityClass);
        if (setup == null) {
            EntityType entity = metamodel.entity(entityClass);
            setup = new EntitySetup();

            boolean idIsUID = UUID.class.equals(entity.getIdType().getJavaType());
            if (idIsUID) {
                Field uidField = (Field) entity.getId(UUID.class).getJavaMember();
                setup.uidGetter(element -> {
                    try {
                        return uidField.get(element);
                    } catch (IllegalAccessException e) {
                        throw new E2SerializationException(e);
                    }
                });
            }
        }
        return setup;
    }


    public static class EntitySetup<T> {
        private boolean valid = false;

        private boolean synth;
        private Function<Object, UUID> uidGetter;
        private Set<String> skip = new HashSet<>();

        EntitySetup() {}

        private void validate() {
            if (!valid) {
                if (uidGetter != null) {
                    synth = false;

                } else {
                    synth = true;
                    uidGetter = element -> UUID.randomUUID();
                }

                valid = true;
            }
        }


        @SuppressWarnings("unchecked")
        public EntitySetup<T> uidGetter(Function<T, UUID> uidGetter) {
            this.uidGetter = (Function<Object, UUID>) uidGetter;
            return this;
        }

        public EntitySetup<T> skip(String... attributeNames) {
            skip.addAll(Arrays.asList(attributeNames));
            return this;
        }


        boolean synth() {
            validate();
            return synth;
        }

        UUID getUid(Object element) {
            validate();
            return uidGetter.apply(element);
        }

        Set<String> skipped() {
            return skip;
        }
   }

}