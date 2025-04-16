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

package org.eclipse.osgi.technology.incubator.command.converter.bundle;

import java.util.ArrayList;
import java.util.List;

import org.apache.felix.service.command.Converter;
import org.eclipse.osgi.technology.incubator.command.util.GlobFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;


@org.osgi.annotation.bundle.Capability(
    namespace = "osgi.commands",
    name = "converter.bundle",
    version = "1.0.0"
)
public class BundleConverter implements Converter {

    private BundleContext context;

    public BundleConverter(BundleContext context) {
        this.context = context;
    }

    @Override
    public Object convert(Class<?> desiredType, Object sourceObject) throws Exception {
        if (desiredType == Bundle.class) {
            if (sourceObject instanceof Number n) {
                var bundle = context.getBundle(n.longValue());
                if (bundle != null) {
                    return bundle;
                }
            } else if (sourceObject instanceof String sourceString) {
                try {
                    var bundleId = Long.parseLong(sourceString);
                    return context.getBundle(bundleId);
                } catch (Exception e) {

                }

                for (Bundle b : context.getBundles()) {

                    if (b.getSymbolicName().equals(sourceString)) {
                        return b;
                    }
                }

                GlobFilter glob = new GlobFilter(sourceString);
                List<Bundle> matchingBundles = new ArrayList<>();
                for (Bundle b : context.getBundles()) {
                    if (glob.matches(b.getSymbolicName())) {
                        matchingBundles.add(b);
                    }
                }

                if (matchingBundles.size() == 1) {
                    return matchingBundles.get(0);
                }
            }

        }
        return null;
    }

    @Override
    public CharSequence format(Object target, int level, Converter escape) throws Exception {
        return null;
    }

}
