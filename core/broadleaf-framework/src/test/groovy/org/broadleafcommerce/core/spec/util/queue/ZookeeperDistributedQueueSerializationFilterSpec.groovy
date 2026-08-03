/*-
 * #%L
 * BroadleafCommerce Framework
 * %%
 * Copyright (C) 2009 - 2026 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
package org.broadleafcommerce.core.spec.util.queue

import org.apache.solr.common.SolrInputDocument
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand
import org.broadleafcommerce.core.search.service.solr.indexer.SiteReindexCommand
import org.broadleafcommerce.core.util.queue.ZookeeperDistributedQueue
import spock.lang.Specification
import spock.lang.Unroll

import javax.naming.ldap.Rdn

import java.io.ObjectInputFilter.Status

class ZookeeperDistributedQueueSerializationFilterSpec extends Specification {

    ObjectInputFilter filter = ObjectInputFilter.Config
            .createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN)

    def "Queue commands are readable through the deserialization filter"() {
        given: "a command of the type that this queue holds"
        SolrInputDocument doc = new SolrInputDocument()
        doc.addField('id', 'product:1')
        IncrementalUpdateCommand command = new IncrementalUpdateCommand([doc], ['id:product\\:2'])

        when:
        Object result = readFiltered(serialize(command))

        then:
        result instanceof IncrementalUpdateCommand
        result.solrInputDocuments.first().getFieldValue('id') == 'product:1'
        result.deleteQueries == ['id:product\\:2']
    }

    def "The max capacity config value is readable through the deserialization filter"() {
        expect:
        readFiltered(serialize(500)) == 500
    }

    def "Classes outside of the allow list are rejected"() {
        when:
        readFiltered(serialize(new File('/tmp/payload')))

        then:
        thrown(InvalidClassException)
    }

    def "Classes outside of the allow list are rejected when nested inside an allowed type"() {
        when:
        readFiltered(serialize(new HashMap<String, Object>(['payload': new File('/tmp/payload')])))

        then:
        thrown(InvalidClassException)
    }

    @Unroll
    def "#className is rejected by the deserialization filter"() {
        expect:
        checkClass(Class.forName(className)) == Status.REJECTED

        where:
        className << [
                'java.io.File',
                'java.rmi.server.UnicastRemoteObject',
                'javax.management.BadAttributeValueExpException',
                'javax.naming.ldap.Rdn',
                'org.springframework.beans.factory.ObjectFactory'
        ]
    }

    @Unroll
    def "#clazz.name is allowed by the deserialization filter"() {
        expect:
        checkClass(clazz) == Status.ALLOWED

        where:
        clazz << [
                Integer,
                String,
                ArrayList,
                HashMap,
                SolrInputDocument,
                SiteReindexCommand,
                IncrementalUpdateCommand
        ]
    }

    def "Patterns prepended to the default pattern extend the allow list"() {
        given: "the pattern that is built when additional allowed classes are configured"
        String pattern = 'java.io.File;' + ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN
        ObjectInputFilter extendedFilter = ObjectInputFilter.Config.createFilter(pattern)

        expect:
        checkClass(extendedFilter, File) == Status.ALLOWED
        checkClass(extendedFilter, Rdn) == Status.REJECTED
    }

    private byte[] serialize(Serializable obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        new ObjectOutputStream(baos).withCloseable { it.writeObject(obj) }
        return baos.toByteArray()
    }

    private Object readFiltered(byte[] bytes) {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
        ois.setObjectInputFilter(filter)
        return ois.withCloseable { it.readObject() }
    }

    private Status checkClass(Class<?> clazz) {
        return checkClass(filter, clazz)
    }

    private Status checkClass(ObjectInputFilter objectInputFilter, Class<?> clazz) {
        return objectInputFilter.checkInput(new FilterInfoStub(clazz))
    }

    private static class FilterInfoStub implements ObjectInputFilter.FilterInfo {

        private final Class<?> clazz

        FilterInfoStub(Class<?> clazz) {
            this.clazz = clazz
        }

        @Override
        Class<?> serialClass() {
            return clazz
        }

        @Override
        long arrayLength() {
            return -1L
        }

        @Override
        long depth() {
            return 1L
        }

        @Override
        long references() {
            return 1L
        }

        @Override
        long streamBytes() {
            return 1L
        }

    }

}
