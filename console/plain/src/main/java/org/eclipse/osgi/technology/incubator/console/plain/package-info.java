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
@org.osgi.annotation.bundle.Capability(
    attribute = "implementation.name=tech.shell.plain",
    namespace = "org.apache.felix.gogo",
    name = "shell.implementation",
    version = "1.0.0"
)
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.osgi.technology.incubator.console.plain;