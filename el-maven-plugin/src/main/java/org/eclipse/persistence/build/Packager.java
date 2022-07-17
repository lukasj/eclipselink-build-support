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

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class Packager {

    private final MavenArchiver archiver;
    private final Log log;
    private File confDir;
    private final List<Path> resources;

    Packager(Packager p) {
        this(p.archiver.getArchiver(), p.log);
        File f = p.archiver.getArchiver().getDestFile();
        archiver.getArchiver().addFile(f, f.getName());
    }

    Packager(JarArchiver jarArchiver, Log log) {
        archiver = new MavenArchiver();
        archiver.setCreatedBy("EclipseLink Build Plugin", PackagerMojo.PLUGIN_GROUP_ID, PackagerMojo.PLUGIN_ARTIFACT_ID);
        archiver.setArchiver(jarArchiver);
        this.log = log;
        resources = new ArrayList<>(1);
    }

    public void setTarget(File destFile) {
        archiver.setOutputFile(destFile);
        archiver.getArchiver().setDestFile(destFile);
    }

    public void setOutputTimestamp(String outputTimestamp) {
        archiver.configureReproducible(outputTimestamp);
    }

    public void setConfDir(File confDir) {
        this.confDir = confDir;
    }

    public void addFile(File file) {
        addFile(file, "");
    }

    public void addFile(File file, String prefix) {
        if (file.exists() && file.isFile()) {
            String destName = stripVersion(file.getName());
            archiver.getArchiver().addFile(file, prefix + destName);
            log.debug("adding file: " + prefix + file.getName());
        } else {
            log.debug("skipping file: " + prefix + file.getName());
        }
    }

    public void addExpanded(File archive) {
        addExpanded(archive, null);
    }

    public void addExpanded(File archive, String exclusionFilter) {
        if (archive.exists() && archive.isFile()) {
            archiver.getArchiver().addArchivedFileSet(archive, null,
                    exclusionFilter == null ? null : new String[]{exclusionFilter});
            log.debug("adding expanded archive: " + archive.getName() + ", exclusions: " + exclusionFilter);
        } else {
            log.debug("skipping expanded archive: " + archive.getName() + ", exclusions: " + exclusionFilter);
        }
    }

    public void addClasses(File root) {
        addClasses(root, null);
    }

    public void addClasses(File root, String[] exclusionFilter) {
        if (root.exists() && root.isDirectory()) {
            archiver.getArchiver().addDirectory(root, null, exclusionFilter);
            log.debug("adding directory: " + root.getName() + ", exclusions: " + Arrays.toString(exclusionFilter));
        } else {
            log.debug("skipping directory: " + root.getName() + ", exclusions: " + Arrays.toString(exclusionFilter));
        }
    }

    public void addResources(Path resourceDir) {
        resources.add(resourceDir);
    }

    public void addTemplate(String template) {
        File t = new File(confDir, template);
        if (t.exists() && t.isFile()) {
            archiver.getArchiver().addFile(t, "META-INF/templates/" + t.getName());
            log.debug("adding template: " + t.getName());
        } else {
            log.debug("skipping template: " + t.getName());
        }
    }

    public void createArchive(MavenProject project, MavenSession session, MavenResourcesFiltering filtering, MavenArchiveConfiguration archive)
            throws MavenFilteringException, DependencyResolutionRequiredException, IOException, ManifestException {
        List<Resource> res = getResources();
        if (res.isEmpty()) {
            log.debug("skipping directory: " + confDir.getName());
        } else {
            log.debug("filtering resources: " + confDir.getName());
            File filtered = filterResources(project, session, filtering, res);
            log.debug("adding resources: " + filtered.getName());
            archiver.getArchiver().addDirectory(filtered);
        }
        archiver.createArchive(session, project, archive);
    }

    private File filterResources(MavenProject project, MavenSession session, MavenResourcesFiltering filtering, List<Resource> resources) throws MavenFilteringException {
        File destDir = Paths.get(project.getBuild().getDirectory()).resolve(PackagerMojo.WORK_DIR.resolve(confDir.getName())).toFile();
        MavenResourcesExecution resourceExec = new MavenResourcesExecution(
                resources, destDir, project, project.getProperties().getProperty("project.build.sourceEncoding"),
                Collections.emptyList(), Collections.emptyList(), session);
        resourceExec.addFilerWrapperWithEscaping(new PropertiesValueSource(project.getProperties()),
                "@", "@", "\\", true);
        filtering.filterResources(resourceExec);
        return destDir;
    }

    private String stripVersion(String s) {
        int x = s.indexOf('-');
        if (x > 1 && s.contains("member_")) {
            int y = s.lastIndexOf('-') + 1;
            return s.substring(0, x) + "_" + s.substring(y);
        }
        return s;
    }

    private List<Resource> getResources() {
        List<Resource> res = new ArrayList<>();
        if (confDir.exists() && confDir.isDirectory()) {
            res.add(createResource(confDir));
        }
        resources.forEach((path) -> res.add(createResource(path.toFile())));
        return res;
    }

    private Resource createResource(File dir) {
        Resource r = new Resource();
        r.setDirectory(dir.getAbsolutePath());
        r.setFiltering(true);
        return r;
    }
}
