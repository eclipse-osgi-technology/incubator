/**
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Stefan Bischof - initial
 */
package org.eclipse.osgi.technology.incubator.command.osgi.framework.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.osgi.framework.BundleContext;
import org.osgi.framework.dto.FrameworkDTO;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;

public class Test {

    @InjectBundleContext
    BundleContext context;

    @InjectService(cardinality = 1, timeout = 200)
    CommandProcessor commandProcessor;

    ByteArrayInputStream in;
    ByteArrayOutputStream out;
    ByteArrayOutputStream err;
    CommandSession session;

    @BeforeEach
    void beforeEach() {

        in = new ByteArrayInputStream("".getBytes());
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();

        session = commandProcessor.createSession(in, out, err);
    }

    @org.junit.jupiter.api.Test
    void testName() throws Exception {
        Object o = session.execute("lb");

        assertThat(o).isInstanceOf(List.class).asInstanceOf(InstanceOfAssertFactories.LIST).hasSizeGreaterThan(10);
    }

    @org.junit.jupiter.api.Test
    void testFrameworkDTOCommand() throws Exception {
        Object result = session.execute("frameworkDTO");
        assertThat(result).isInstanceOf(FrameworkDTO.class);

        FrameworkDTO frameworkDTO = (FrameworkDTO) result;
        assertThat(frameworkDTO.bundles).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void testBundleDTOCommand() throws Exception {
        Object result = session.execute("bundleDTO");
        assertThat(result).isInstanceOf(List.class).asInstanceOf(InstanceOfAssertFactories.LIST).isNotEmpty();

    }

    @org.junit.jupiter.api.Test
    void testBundleDTOByIdCommand() throws Exception {
        Object result = session.execute("bundleDTO 0");
        assertThat(result).hasFieldOrProperty("id");
    }

    @org.junit.jupiter.api.Test
    void testServiceReferenceDTOCommand() throws Exception {
        Object result = session.execute("serviceReferenceDTO");
        assertThat(result).isInstanceOf(List.class).asInstanceOf(InstanceOfAssertFactories.LIST).isNotEmpty();
    }

    @org.junit.jupiter.api.Test
    void testServiceReferenceDTOByIdCommand() throws Exception {
        Object allServices = session.execute("serviceReferenceDTO");
        assertThat(allServices).isInstanceOf(List.class).asInstanceOf(InstanceOfAssertFactories.LIST).isNotEmpty();

        List<?> services = (List<?>) allServices;
        ServiceReferenceDTO firstDTO = (ServiceReferenceDTO) services.get(0);

        long serviceId = firstDTO.id;
        Object singleService = session.execute("serviceReferenceDTO " + serviceId);
        assertThat(singleService).isInstanceOf(ServiceReferenceDTO.class);

        ServiceReferenceDTO singleRef = (ServiceReferenceDTO) singleService;
        assertThat(singleRef.id).isEqualTo(serviceId);
    }

    @org.junit.jupiter.api.Test
    void testSrvCommand() throws Exception {
//        Object result = session.execute("srv 1");
//        assertThat(result).isInstanceOf(List.class).asInstanceOf(InstanceOfAssertFactories.LIST).isNotEmpty();
    }

    @org.junit.jupiter.api.Test
    void testBsnCommand() throws Exception {
        Object result = session.execute("bsn 0");
        assertThat(result).isInstanceOf(String.class).asString().isNotBlank();
    }

    @org.junit.jupiter.api.Test
    void testStartlevelCommand() throws Exception {
        Object result = session.execute("startlevel");
        assertThat(result).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void testStartlevelOfBundle() throws Exception {
        Object result = session.execute("startlevel 0");
        assertThat(result).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void testHeadersCommand() throws Exception {
        String command = "headers -h Bundle-Name";
        Object result = session.execute(command);

        assertThat(result).isInstanceOf(Map.class);

    }

}
