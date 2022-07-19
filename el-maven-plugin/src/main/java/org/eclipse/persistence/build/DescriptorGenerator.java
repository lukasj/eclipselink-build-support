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

import org.apache.maven.plugin.logging.Log;

import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

final class DescriptorGenerator {

    private static Templates puTemplate;
    private static Templates ejbTemplate;
    private boolean generateEJB, generatePU;
    private final Log log;
    private final Path sourcePu;

    DescriptorGenerator(Path sourcePu, Log log) {
        this.log = log;
        this.sourcePu = sourcePu;
        generateEJB = generatePU = true;
    }

    void ejbDescriptor(boolean generateEJB) {
        this.generateEJB = generateEJB;
    }

    void persistenceDescriptor(boolean generatePU) {
        this.generatePU = generatePU;
    }

    void generate(Path destDir, Map<String, Object> options) throws IOException, TransformerException {
        if (!generatePU && !generateEJB) {
            log.info("Not generating server-side descriptors...");
            return;
        }
        if (generatePU) {
            Path dest = destDir.resolve(PackagerMojo.PERSISTENCE_DESC);
            log.info(String.format("Generating %s to: %s", PackagerMojo.PERSISTENCE_DESC.getFileName(), dest));
            createDescriptor(dest, options, getPersistenceTransformer());
        } else {
            log.info(String.format("Not generating %s...", PackagerMojo.PERSISTENCE_DESC.getFileName()));
        }
        if (generateEJB) {
            Path dest = destDir.resolve(PackagerMojo.EJB_DESC);
            log.info(String.format("Generating %s to: %s", PackagerMojo.EJB_DESC.getFileName(), dest));
            createDescriptor(dest, options, getEJBTransformer());
        } else {
            log.info(String.format("Not generating %s...", PackagerMojo.EJB_DESC.getFileName()));
        }
    }

    private void createDescriptor(Path output, Map<String, Object> options, Transformer transformer) throws TransformerException, IOException {
        final Map<String, Object> opts = Objects.requireNonNull(options);
        final Path dest = Objects.requireNonNull(output);
        final Transformer t = Objects.requireNonNull(transformer);
        opts.forEach(transformer::setParameter);
        Files.createDirectories(dest.getParent());
        if (Files.exists(dest)) {
            Files.delete(dest);
        }
        try (Reader r = Files.newBufferedReader(sourcePu);
             Writer w = Files.newBufferedWriter(Files.createFile(dest))) {
            t.transform(new StreamSource(r), new StreamResult(w));
        }
    }

    private Transformer getEJBTransformer() throws TransformerConfigurationException {
        if (ejbTemplate == null) {
            initialize();
        }
        return ejbTemplate.newTransformer();
    }

    private Transformer getPersistenceTransformer() throws TransformerConfigurationException {
        if (puTemplate == null) {
            initialize();
        }
        return puTemplate.newTransformer();
    }

    private synchronized void initialize() throws TransformerConfigurationException {
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        puTemplate = transformerFactory.newTemplates(new StreamSource(DescriptorGenerator.class.getResourceAsStream("pu.xsl")));
        ejbTemplate = transformerFactory.newTemplates(new StreamSource(DescriptorGenerator.class.getResourceAsStream("ejb.xsl")));
    }
}
