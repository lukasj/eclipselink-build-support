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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class Packager {

    /**
     * The plugin groupId.
     */
    private static final String PLUGIN_GROUP_ID = "org.eclipse.persistence";

    /**
     * The plugin artifactId.
     */
    private static final String PLUGIN_ARTIFACT_ID = "eclipselink-testbuild-plugin";

    private final MavenArchiver archiver;
    private final Log log;
    private File confDir;

    Packager(Packager p) {
        this(p.archiver.getArchiver(), p.log);
        File f = p.archiver.getArchiver().getDestFile();
        archiver.getArchiver().addFile(f, f.getName());
    }

    Packager(JarArchiver jarArchiver, Log log) {
        archiver = new MavenArchiver();
        archiver.setCreatedBy("EclipseLink Build Plugin", PLUGIN_GROUP_ID, PLUGIN_ARTIFACT_ID);
        archiver.setArchiver(jarArchiver);
        this.log = log;
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
        if (confDir.exists() && confDir.isDirectory()) {
            log.debug("filtering resources: " + confDir.getName());
            File filtered = filterResources(project, session, filtering, confDir);
            log.debug("adding resources: " + filtered.getName());
            archiver.getArchiver().addDirectory(filtered);
        } else {
            log.debug("skipping directory: " + confDir.getName());
        }
        archiver.createArchive(session, project, archive);
    }

    private File filterResources(MavenProject project, MavenSession session, MavenResourcesFiltering filtering, File resources) throws MavenFilteringException {
        File destDir = new File(project.getBuild().getDirectory(), "eclipselink-packager/" + resources.getName());
        Resource r = new Resource();
        r.setDirectory(resources.getAbsolutePath());
        r.setFiltering(true);
        MavenResourcesExecution resourceExec = new MavenResourcesExecution(
                List.of(r), destDir, project, project.getProperties().getProperty("project.build.sourceEncoding"),
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

}
