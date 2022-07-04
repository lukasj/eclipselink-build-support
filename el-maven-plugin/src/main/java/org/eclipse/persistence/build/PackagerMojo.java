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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mojo(name="ear", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class PackagerMojo extends AbstractMojo {

//    @Component
//    private TestEar te;

    /**
     * The archivers.
     */
    @Component
    private Map<String, Archiver> archivers;

    @Component
    private Map<String, UnArchiver> unArchivers;

    /**
     * The Maven project this mojo executes on.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory containing the generated JAR.
     */
    @Parameter( defaultValue = "${project.build.directory}", required = true )
    private File outputDirectory;

    /**
     * Directory containing the classes and resource files that should be packaged into the JAR.
     */
    @Parameter( defaultValue = "${project.build.outputDirectory}", required = true )
    private File classesDirectory;

    /**
     * Directory containing the test classes and resource files that should be packaged into the JAR.
     */
    @Parameter( defaultValue = "${project.build.testOutputDirectory}", required = true )
    private File testClassesDirectory;

    /**
     * Set this to <code>true</code> to bypass ear-test-jar generation.
     */
    @Parameter( property = "el.fwk.skip" )
    private boolean skip;

    /**
     * Timestamp for reproducible output archive entries.
     */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    private String outputTimestamp;

    /**
     * Ejb-jar resources, overrides defaults
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/resources-server")
    private File ejbConf;

    /**
     * Ear resources
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/resources-ear")
    private File earConf;

    /**
     * Name of the generated JAR.
     */
    @Parameter( defaultValue = "${project.build.finalName}", readonly = true )
    private String finalName;

    /**
     * The {@link MavenSession}.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * The archive configuration to use.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    @Component
    private MavenResourcesFiltering mavenResourcesFiltering;

    /**
     * The entry point to Maven Artifact Resolver, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter( defaultValue = "${repositorySystemSession}", readonly = true )
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution.
     */
    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true )
    private List<RemoteRepository> remoteRepos;

    /**
     * Artifact Ids of project dependencies to be included in the target archive.
     */
    @Parameter
    private List<String> libs;

    /**
     * Archive to build. Default is {@code EAR}.
     */
    @Parameter(defaultValue = "EAR")
    private String mode;

    /**
     * Content to exclude from the jpa.test.framework in the target EJB jar
     */
    @Parameter(property = "el.fwk.exclusionFilter", defaultValue = "%regex[.*TestRunner[0-9].*]")
    private String fwkExclusionFilter;

    @Component
    private MavenProjectHelper helper;

    public PackagerMojo() {
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            getLog().info("pom projects not supported, skipping...");
            return;
        }
        final boolean testSkip = Boolean.getBoolean(project.getProperties().getProperty("maven.test.skip"));
        if (testSkip) {
            getLog().info("required tests were not built, skipping...");
            return;
        }
        if (skip) {
            getLog().info("skipping...");
            return;
        }

        File destJar = new File(outputDirectory, finalName + "_ejb.jar");
        Packager p = new Packager((JarArchiver) archivers.get("jar"), getLog());
        p.setTarget(destJar);
        p.setOutputTimestamp(outputTimestamp);
        p.setConfDir(ejbConf);
        if (!project.getArtifactId().contains("member")) {
            try {
                File fwk = getResolved("org.eclipse.persistence.jpa.test.framework");
                p.addExpanded(fwk, fwkExclusionFilter);
            } catch (ArtifactResolutionException e) {
                throw new RuntimeException(e);
            }
        }
        Dependency memberDep = getMemberArtifact();
        if (memberDep != null) {
            try {
                Artifact member = DependencyResolver.resolveArtifact(memberDep, remoteRepos, repoSystem, repoSession);
                p.addExpanded(member.getFile());
            } catch (Throwable t) {
                throw new MojoExecutionException(t.getMessage(), t);
            }
        }
        p.addClasses(classesDirectory, new String[]{"META-INF/persistence.xml", "META-INF/sessions.xml", "*.jar"});
        p.addClasses(testClassesDirectory);
        p.addTemplate("META-INF/persistence.xml");
        p.addTemplate("META-INF/sessions.xml");
        try {
            p.createArchive(project, session, mavenResourcesFiltering, archive);
        } catch (Throwable t) {
            throw new MojoExecutionException(t.getMessage(), t);
        }
        helper.attachArtifact(project, "jar", "ejb", destJar);

        if ("EAR".equalsIgnoreCase(mode)) {
            p = new Packager(p);
            destJar = new File(outputDirectory, finalName + ".ear");
            p.setTarget(destJar);
            p.setOutputTimestamp(outputTimestamp);
            p.setConfDir(earConf);
            try {
                File f = getResolved("org.eclipse.persistence.core.test.framework");
                p.addFile(f, "lib/");
                f = getResolved("junit");
                p.addFile(f, "lib/");
                for (Dependency testArtifact : getTestArtifacts()) {
                    try {
                        Artifact tests = DependencyResolver.resolveArtifact(testArtifact, remoteRepos, repoSystem, repoSession);
                        p.addFile(tests.getFile(), "lib/");
                    } catch (Throwable t) {
                        throw new MojoExecutionException(t.getMessage(), t);
                    }
                }
            } catch (ArtifactResolutionException e) {
                throw new RuntimeException(e);
            }
            List<Dependency> memberDeps = getMemberArtifacts();
            for (Dependency member : memberDeps) {
                try {
                    Artifact m = DependencyResolver.resolveArtifact(member, remoteRepos, repoSystem, repoSession);
                    p.addFile(m.getFile());
                } catch (Throwable t) {
                    throw new MojoExecutionException(t.getMessage(), t);
                }
            }
            try {
                p.createArchive(project, session, mavenResourcesFiltering, archive);
            } catch (Throwable t) {
                throw new MojoExecutionException(t.getMessage(), t);
            }
            helper.attachArtifact(project, "jar", "ear", destJar);
        }
    }

    private Dependency getArtifact(String artifactId) {
        for (Dependency dependency : project.getDependencies()) {
            if (dependency.getArtifactId().equals(artifactId)) {
                return dependency;
            }
        }
        return null;
    }

    private Dependency getMemberArtifact() {
        for (Dependency dependency : project.getDependencies()) {
            String classifier = dependency.getClassifier();
            if (classifier != null && classifier.contains("member")) {
                return dependency;
            }
        }
        return null;
    }

    private List<Dependency> getMemberArtifacts() {
        List<Dependency> members = new ArrayList<>();
        for (Dependency dependency : project.getDependencies()) {
            if (dependency.getArtifactId().contains("member")) {
                dependency.setClassifier("ejb");
                members.add(dependency);
            }
        }
        return members;
    }

    private List<Dependency> getTestArtifacts() {
        List<Dependency> tests = new ArrayList<>();
        for (Dependency dependency : project.getDependencies()) {
            if (dependency.getType().equals("test-jar")) {
                tests.add(dependency);
            }
        }
        return tests;
    }

    private File getResolved(String artifactId) throws ArtifactResolutionException {
        Dependency dep = getArtifact(artifactId);
        if (dep != null && !project.getArtifactId().contains("member")) {
            return DependencyResolver.resolveArtifact(dep, remoteRepos, repoSystem, repoSession).getFile();
        }
        return null;
    }

}
