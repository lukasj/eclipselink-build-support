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
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.interpolation.ValueSource;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name="package-testapp", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public final class PackagerMojo extends AbstractMojo {

    static final Path EJB_DESC = Path.of("META-INF", "ejb-jar.xml");
    static final Path PERSISTENCE_DESC = Path.of("META-INF", "persistence.xml");
    static final Path WORK_DIR = Path.of("eclipselink-packager");

    /**
     * The plugin groupId.
     */
    static final String PLUGIN_GROUP_ID = "org.eclipse.persistence";

    /**
     * The plugin artifactId.
     */
    static final String PLUGIN_ARTIFACT_ID = "eclipselink-testbuild-plugin";

    private static final Map<String, String> RUNNERS = Map.of(
            "org/eclipse/persistence/testing/framework/jpa/server/TestRunner.class", "",
            "org/eclipse/persistence/testing/framework/jpa/server/GenericTestRunner.class", "GenericTestRunner",
            "org/eclipse/persistence/testing/framework/jpa/server/SingleUnitTestRunnerBean.class", "SingleUnitTestRunner",
            "org/eclipse/persistence/testing/framework/jpa/server/TestRunner1Bean.class", "TestRunner1",
            "org/eclipse/persistence/testing/framework/jpa/server/TestRunner2Bean.class", "TestRunner2",
            "org/eclipse/persistence/testing/framework/jpa/server/TestRunner3Bean.class", "TestRunner3",
            "org/eclipse/persistence/testing/framework/jpa/server/TestRunner4Bean.class", "TestRunner4",
            "org/eclipse/persistence/testing/framework/jpa/server/TestRunner5Bean.class", "TestRunner5",
            "org/eclipse/persistence/testing/framework/jpa/server/TestRunner6Bean.class", "TestRunner6"
    );

    private static final Map<String, String> RUNNERS_CACHE = new ConcurrentHashMap<>(2);

    /**
     * The archiver.
     */
    @Component(role = Archiver.class, hint = "jar")
    private JarArchiver archiver;

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
    @Parameter(defaultValue = "${project.basedir}/src/main/resources-ejb")
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
    @Parameter(property = "el.packager.mode", defaultValue = "EAR")
    private String mode;

    /**
     * Archive to build. Default is {@code EAR}.
     */
    @Parameter(property = "el.packager.descriptors", defaultValue = "true")
    private boolean generateDescriptors;

    /**
     * Content to exclude from the jpa.test.framework in the target EJB jar
     */
    @Parameter(property = "el.packager.fwk.exclusionFilter", defaultValue = "%regex[.*TestRunner[0-9].*]")
    private String fwkExclusionFilter;

    @Component
    private MavenProjectHelper helper;

    public PackagerMojo() {
    }

    public void execute() throws MojoExecutionException {
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
        Packager p = new Packager(archiver, getLog());
        p.setTarget(destJar);
        p.setOutputTimestamp(outputTimestamp);
        p.setConfDir(ejbConf);
        try {
            File fwk = getResolved("org.eclipse.persistence.jpa.test.framework");
            if (fwk == null) {
                throw new MojoExecutionException("cannot find dependency on org.eclipse.persistence.jpa.test.framework");
            }
            p.addExpanded(fwk, fwkExclusionFilter);
            if (generateDescriptors) {
                Path puXml = Paths.get(project.getResources().get(0).getDirectory()).resolve(PERSISTENCE_DESC);
                if (Files.isRegularFile(puXml)) {
                    DescriptorGenerator gen = new DescriptorGenerator(puXml, getLog());
                    gen.ejbDescriptor(Files.notExists(ejbConf.toPath().resolve(EJB_DESC)));
                    gen.persistenceDescriptor(Files.notExists(ejbConf.toPath().resolve(PERSISTENCE_DESC)));
                    ValueSource props = new PropertiesValueSource(project.getProperties());
                    Map<String, Object> options = new HashMap<>();
                    options.put("generator.id", String.format("EclipseLink Build Plugin (%s:%s:%s)", PackagerMojo.PLUGIN_GROUP_ID, PackagerMojo.PLUGIN_ARTIFACT_ID, getPluginVersion()));
                    options.put("data-source-type", props.getValue("persistence-unit.data-source-type"));
                    options.put("data-source-name", props.getValue("persistence-unit.data-source-name"));
                    options.put("db.platform", props.getValue("db.platform"));
                    options.put("server.platform", props.getValue("server.platform"));
                    options.put("server.weaving", props.getValue("persistence-unit.server-weaving"));
                    try {
                        options.put("testRunners", getRunners(fwk, fwkExclusionFilter));
                    } catch (UnsupportedOperationException uoe) {
                        gen.ejbDescriptor(false);
                        getLog().warn(uoe.getMessage());
                    }
                    Path generatedFolder = Paths.get(project.getBuild().getDirectory()).resolve(WORK_DIR.resolve("generated"));
                    gen.generate(generatedFolder, options);
                    p.addResources(generatedFolder);
                } else {
                    getLog().warn(String.format("Cannot find %s resource to generate server-side descriptors from.", PERSISTENCE_DESC));
                }
            }
        } catch (ArtifactResolutionException | IOException | TransformerException e) {
            throw new MojoExecutionException(e);
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
        for (Dependency testArtifact : getTestArtifacts()) {
            try {
                Artifact tests = DependencyResolver.resolveArtifact(testArtifact, remoteRepos, repoSystem, repoSession);
                p.addExpanded(tests.getFile(), "%regex[.*META-INF/.*]");
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
                if (f == null) {
                    throw new MojoExecutionException("cannot find dependency on org.eclipse.persistence.core.test.framework");
                }
                p.addFile(f, "lib/");
                f = getResolved("junit");
                if (f == null) {
                    throw new MojoExecutionException("cannot find dependency on junit");
                }
                p.addFile(f, "lib/");
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
            if ("test-jar".equals(dependency.getType()) || "model".equals(dependency.getClassifier())) {
                tests.add(dependency);
            }
        }
        return tests;
    }

    private File getResolved(String artifactId) throws ArtifactResolutionException {
        Dependency dep = getArtifact(artifactId);
        if (dep != null) {
            return DependencyResolver.resolveArtifact(dep, remoteRepos, repoSystem, repoSession).getFile();
        }
        return null;
    }

    private String getRunners(File file, String filter) {
        return RUNNERS_CACHE.computeIfAbsent(filter, (f) -> {
            Set<String> result = new HashSet<>();
            if (f.startsWith("%regex[")) {
                final Pattern pattern = Pattern.compile(f.substring(7, f.lastIndexOf(']')));
                try (JarFile jf = new JarFile(file)) {
                    try (Stream<JarEntry> stream = jf.stream()) {
                        stream.filter((x) -> x.getName().contains("TestRunner"))
                                .filter((x) -> !(pattern.matcher(x.getName()).matches()))
                                .map((x) -> RUNNERS.getOrDefault(x.getName(), x.getName().substring(x.getName().lastIndexOf('/') + 1, x.getName().lastIndexOf('.'))))
                                .collect(Collectors.toCollection(() -> result));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                //TODO
                throw new UnsupportedOperationException("non-regex filters are not supported yet, ejb-jar.xml won't be generated.");
            }
            return String.join(" ", result).trim();
        });
    }

    private String getPluginVersion() {
        for (Plugin p: project.getBuildPlugins()) {
            if (PLUGIN_GROUP_ID.equals(p.getGroupId()) && PLUGIN_ARTIFACT_ID.equals(p.getArtifactId())) {
                return p.getVersion();
            }
        }
        return "unknown";
    }
}
