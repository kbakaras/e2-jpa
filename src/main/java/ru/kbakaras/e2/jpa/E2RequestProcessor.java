package ru.kbakaras.e2.jpa;

import ru.kbakaras.e2.message.E2;
import ru.kbakaras.e2.message.E2EntityRequest;
import ru.kbakaras.e2.message.E2Request;
import ru.kbakaras.e2.message.E2Response;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import java.util.ArrayList;
import java.util.List;

public class E2RequestProcessor {
    private E2Request request;
    private E2Response response;
    private E2Serializer e2Serializer;

    private E2Metamodel metamodel;

    private EntityManager em;

    public E2RequestProcessor(E2Request request, E2Metamodel e2Metamodel, EntityManager entityManager) {
        this.request = request;
        this.em = entityManager;
        this.metamodel = e2Metamodel;

        response = new E2Response(E2.ELEMENT);
        e2Serializer = new E2Serializer(e2Metamodel,
                response.addSystemResponse(
                        e2Metamodel.getSystemUid().toString(),
                        e2Metamodel.getSystemName())
        );
    }

    public void process() {
        request.entities().forEach(this::processEntityRequest);
    }

    @SuppressWarnings("unchecked")
    private void processEntityRequest(E2EntityRequest er) {
        try {

            Class clazz = Class.forName(er.entityName());
            EntityType<?> meta = em.getMetamodel().entity(clazz);

            CriteriaBuilder cBuilder = em.getCriteriaBuilder();
            CriteriaQuery cQuery = cBuilder.createQuery(clazz);
            Root cRoot = cQuery.from(clazz);

            List<Predicate> predicates = new ArrayList<>();
            er.filters().forEach(filter -> {
                Class<?> attributeClass = meta.getAttribute(filter.attributeName()).getJavaType();

                Object value = metamodel.deserializeValue(attributeClass, filter.value().string());

                if (filter.condition().equals("equal")) {
                    predicates.add(cBuilder.equal(cRoot.get(filter.attributeName()), value));
                } else if (filter.condition().equals("like")) {
                    predicates.add(cBuilder.like(cRoot.get(filter.attributeName()), value.toString()));
                }
            });

            cQuery.select(cRoot).where(predicates.toArray(new Predicate[0]));
            List list = em.createQuery(cQuery).getResultList();

            for (Object element: list) {
                e2Serializer.serialize(element, true);
            }

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public E2Response response() {
        return this.response;
    }
}