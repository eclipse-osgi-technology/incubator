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
package org.eclipse.osgi.technology.incubator.command.osgi.service.scr;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.eclipse.osgi.technology.incubator.command.util.DisplayUtil;
import org.eclipse.osgi.technology.incubator.command.util.dtoformatter.DTOFormatter;
import org.eclipse.osgi.technology.incubator.command.util.dtoformatter.Wrapper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.component.runtime.dto.SatisfiedReferenceDTO;
import org.osgi.service.component.runtime.dto.UnsatisfiedReferenceDTO;
import org.osgi.util.tracker.ServiceTracker;

public class ScrCommands implements Closeable {

    final ServiceTracker<ServiceComponentRuntime, ServiceComponentRuntime> scr;
    final BundleContext context;
    final AtomicLong componentDescriptionNextIndex = new AtomicLong(-1);
    final Map<ComponentDescriptionId, Long> descriptionToIndex = new HashMap<>();

    public ScrCommands(BundleContext context, DTOFormatter formatter) {
        this.context = context;
        dtos(formatter);
        scr = new ServiceTracker<>(context, ServiceComponentRuntime.class, null);
        scr.open();
    }

    @Descriptor("Show the list of available components")
    public Object ds(
            @Parameter(names = { "-c", "--config" }, presentValue = "true", absentValue = "false") boolean configs,
            @Parameter(names = { "-b", "--bundle" }, absentValue = "0") Bundle bs[]) {

        if (bs == null || bs.length == 1 && bs[0].getBundleId() == 0) {
            bs = new Bundle[0];
        }

        if (configs) {
            return getScr().getComponentDescriptionDTOs(bs);
        } else {
            return getScr().getComponentDescriptionDTOs(bs).stream()
                    .flatMap(d -> getScr().getComponentConfigurationDTOs(d).stream()).collect(Collectors.toList());
        }
    }

    @Descriptor("Show the list of available components")
    public Object ds() {

        return getScr().getComponentDescriptionDTOs(context.getBundles()).stream()
                .flatMap(d -> getScr().getComponentConfigurationDTOs(d).stream()).collect(Collectors.toList());
    }

    //
    // The SCR overrides the formatter
    //
    @Descriptor("Show ds components")
    public Wrapper ds(long component) {
        if (component >= 0) {
            return new Wrapper(dsConfig(component));
        } else {
            return new Wrapper(getDescription(component));
        }
    }

    private ComponentConfigurationDTO dsConfig(long component) {
        return getScr().getComponentDescriptionDTOs().stream()
                .flatMap(d -> getScr().getComponentConfigurationDTOs(d).stream()).filter(c -> component == c.id)
                .findFirst().orElse(null);
    }

    public static class Why {
        public ComponentDescriptionDTO description;
        public ComponentConfigurationDTO configuration;
        public String reason;
        public List<WhyReference> references = new ArrayList<>();
    }

    public static class WhyReference {
        public WhyReference(String name, List<Why> candidates) {
            this.name = name;
            this.candidates = candidates;
        }

        public String name;
        public List<Why> candidates;
    }

    @Descriptor("show a dependency tree if not satisfied")
    public List<WhyReference> why(long component) {
        var ds = dsConfig(component);
        if (ds == null) {
            return null;
        }

        var why = why(ds, new HashSet<>());
        return why.references;
    }

    Why why(ComponentConfigurationDTO ds, Set<Object> set) {
        if (!set.add(ds)) {
            return null;
        }

        var why = new Why();
        why.description = ds.description;
        why.configuration = ds;

        for (UnsatisfiedReferenceDTO unsatisfiedReference : ds.unsatisfiedReferences) {
            var ref = getReference(ds.description, unsatisfiedReference.name);
            why.references.add(new WhyReference(ref.name, candidates(ref, set)));
        }
        return why;
    }

    private List<Why> candidates(ReferenceDTO ref, Set<Object> set) {
        List<Why> candidates = new ArrayList<>();

        List<ComponentDescriptionDTO> collect = getScr().getComponentDescriptionDTOs().stream()
                .filter(cd -> Stream.of(cd.serviceInterfaces).filter(ref.interfaceName::equals).findAny().isPresent())
                .collect(Collectors.toList());

        for (ComponentDescriptionDTO d : collect) {

            if ("REQUIRE".equals(d.configurationPolicy)) {
                var required = new Why();
                required.description = d;
                required.reason = "may require configuration";
                candidates.add(required);
            }

            for (ComponentConfigurationDTO c : getScr().getComponentConfigurationDTOs(d)) {
                var why = why(c, set);
                if (why == null) {
                    var cycle = new Why();
                    cycle.description = d;
                    cycle.configuration = c;
                    cycle.reason = "cycle";
                    candidates.add(cycle);
                } else {
                    candidates.add(why);
                }
            }
        }
        return candidates;
    }

    private ReferenceDTO getReference(ComponentDescriptionDTO description, String name) {
        return Stream.of(description.references).filter(r -> r.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the description " + description + " does not have the reference named " + name));
    }

    private ServiceComponentRuntime getScr() {
        return scr.getService();
    }

    @Override
    public void close() throws IOException {
        scr.close();
    }

    void dtos(DTOFormatter formatter) {
        formatter.build(ComponentDescriptionDTO.class).inspect().fields("*").line().format("id", this::getId)
                .format("bundle", cd -> cd.bundle.id).field("name").field("defaultEnabled").field("immediate")
                .field("activate").field("modified").field("deactivate").field("configurationPolicy")
                .field("configurationPid").field("references").count().part()
                .as(cdd -> String.format("[%s] %s", cdd.bundle.id, cdd.name));

        formatter.build(ComponentConfigurationDTO.class).inspect().format("state", this::state).fields("*").line()
                .field("id").format("state", this::state)
                .format("service", c -> DisplayUtil.objectClass(c.description.serviceInterfaces)).fields("*")
                .remove("properties").remove("description").format("unsatisfiedReferences", this::unsatisfied).part()
                .as(c -> "<" + c.id + "> " + c.description.name);

        formatter.build(SatisfiedReferenceDTO.class).inspect().fields("*").line().fields("*")
                .format("boundServices", u -> shortend(u.boundServices)).part().as(sr -> sr.name);

        formatter.build(ServiceReferenceDTO.class).inspect().fields("*").line().format("id", srd -> srd.id + "")
                .format("service", srd -> DisplayUtil.objectClass(srd.properties)).part()
                .as(sr -> "(" + sr.id + ")" + DisplayUtil.objectClass(sr.properties));

        formatter.build(UnsatisfiedReferenceDTO.class).inspect().fields("*").line().fields("*").part()
                .as(sr -> sr.name);

        formatter.build(ReferenceDTO.class).inspect().fields("*").line().fields("*").part().field("name");

        formatter.build(Unsatisfied.class).inspect().fields("*").line().fields("*").part().as(u -> u.name);

        formatter.build(Why.class).inspect().fields("*").line().format("description", w -> getId(w.description))
                .format("configuration", w -> w.configuration == null ? "" : w.configuration.id).field("reason")
                .field("references").part().as(w -> "<<" + getId(w.description) + ">>");

        formatter.build(WhyReference.class).inspect().fields("*").line().field("name").field("candidates").part()
                .field("name");
    }

    static class ComponentDescriptionId {
        private final String name;
        private final Bundle bundle;

        ComponentDescriptionId(String name, Bundle bundle) {
            this.name = name;
            this.bundle = bundle;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bundle, name);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (getClass() != obj.getClass())) {
                return false;
            }
            var other = (ComponentDescriptionId) obj;
            return Objects.equals(bundle, other.bundle) && Objects.equals(name, other.name);
        }

    }

    long getId(ComponentDescriptionDTO description) {
        return descriptionToIndex.computeIfAbsent(
                new ComponentDescriptionId(description.name, context.getBundle(description.bundle.id)),
                d -> componentDescriptionNextIndex.getAndDecrement());
    }

    ComponentDescriptionDTO getDescription(long id) {
        return descriptionToIndex.entrySet().stream().filter(e -> e.getValue() == id).map(Entry::getKey).findFirst()
                .map(cdi -> getScr().getComponentDescriptionDTO(cdi.bundle, cdi.name)).orElse(null);
    }

    public static class Unsatisfied {
        public String name;
        public String interfaceName;
        public String cardinality;
        public String target;
        public List<Long> candidates;
        public String remark;
    }

    private List<Unsatisfied> unsatisfied(ComponentConfigurationDTO c) {
        return Stream.of(c.unsatisfiedReferences).map(ur -> unsatisfied(c.description, ur))
                .collect(Collectors.toList());
    }

    private Unsatisfied unsatisfied(ComponentDescriptionDTO description, UnsatisfiedReferenceDTO r) {
        var u = new Unsatisfied();
        try {
            var ref = find(description, r.name);
            u.name = r.name;
            u.target = r.target;
            u.interfaceName = DisplayUtil.shorten(ref.interfaceName);
            u.cardinality = ref.cardinality;

            u.candidates = getServiceCandidates(r, ref);

        } catch (InvalidSyntaxException e) {
            u.remark = e.toString();
        }

        return u;

    }

    private List<Long> getServiceCandidates(UnsatisfiedReferenceDTO r, ReferenceDTO ref) throws InvalidSyntaxException {
        var serviceReferences = context.getServiceReferences(ref.interfaceName, r.target);
        if (serviceReferences == null) {
            serviceReferences = new ServiceReference<?>[0];
        }

        return Stream.of(serviceReferences).map(x -> (Long) x.getProperty(Constants.SERVICE_ID))
                .collect(Collectors.toList());
    }

    private ReferenceDTO find(ComponentDescriptionDTO description, String name) {
        for (ReferenceDTO r : description.references) {
            if (r.name.equals(name)) {
                return r;
            }
        }
        throw new IllegalStateException("Not supposed to happen");
    }

    private List<String> shortend(ServiceReferenceDTO[] refs) {
        return Stream.of(refs).map(r -> "<" + r.id + "> " + DisplayUtil.objectClass(r.properties))
                .collect(Collectors.toList());
    }

    private String state(ComponentConfigurationDTO c) {
        return switch (c.state) {
        case ComponentConfigurationDTO.ACTIVE -> "ACTIVE";
        case ComponentConfigurationDTO.UNSATISFIED_CONFIGURATION -> "CONFG?";
        case ComponentConfigurationDTO.UNSATISFIED_REFERENCE -> "REFRN?";
        case ComponentConfigurationDTO.SATISFIED -> "SATSFD";
        default -> c.state + "?";
        };
    }
}
