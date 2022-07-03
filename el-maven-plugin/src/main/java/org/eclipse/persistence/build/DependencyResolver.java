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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.util.List;

/**
 *
 * @author lukas
 */
final class DependencyResolver {

    public static Artifact resolveArtifact(org.apache.maven.model.Dependency d, List<RemoteRepository> remoteRepos,
                                                RepositorySystem repoSystem, RepositorySystemSession repoSession)
            throws ArtifactResolutionException {
        org.eclipse.aether.artifact.DefaultArtifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(d.getGroupId(),d.getArtifactId(), d.getClassifier(), d.getType(), d.getVersion());
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        request.setRepositories(remoteRepos);
        return repoSystem.resolveArtifact(repoSession, request).getArtifact();
    }
}