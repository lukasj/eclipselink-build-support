/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.persistence.build;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

//@Component(role = AbstractMavenLifecycleParticipant.class, hint = "jar")
public class TestEar extends AbstractMavenLifecycleParticipant {
    @Requirement
    private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        logger.info("");
        logger.info("----------------------------{ eclipselink-testear afterProjectsRead }-----------------------------");
    }

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        logger.info("");
        logger.info("----------------------------{ eclipselink-testear afterSessionStart }-----------------------------");
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
        logger.info("");
        logger.info("----------------------------{ eclipselink-testear afterSessionEnd }-----------------------------");
    }
}
