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

import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;

import java.util.Map;
import java.util.Properties;

final class PropertiesValueSource extends PropertiesBasedValueSource {

    public PropertiesValueSource(Properties properties) {
        super(properties);
    }

    @Override
    public Object getValue(String expression) {
        String exp;
        switch (expression) {
            case "default": exp = "persistence-unit.name"; break;
            case "data-source-name" :
            case "session-data-source-name": exp = "persistence-unit.data-source-name"; break;
            case "data-source2-name" : exp = "persistence-unit.data-source2-name"; break;
            case "data-source3-name" : exp = "persistence-unit.data-source3-name"; break;
            case "datasource-type": exp = "persistence-unit.data-source-type"; break;
            case "transaction-type": exp = "persistence-unit.transaction-type"; break;
            case "server-platform": exp = "server.platform"; break;
            case "server-platform-class": return PLATFORM_TO_CLASS.getOrDefault(super.getValue("server.platform"), "server-platform-class");
            case "database-platform": exp = "db.platform"; break;
            case "database2-platform": exp = "db2.platform"; break;
            case "database3-platform": exp = "db3.platform"; break;
            case "server-weaving": exp = "persistence-unit.server-weaving"; break;
            case "eclipselink.logging.level": return ((String) super.getValue("eclipselink.logging.level")).toLowerCase();
            default:
                exp = expression;
        }
//        System.out.println("looking for: " + expression + " - exp - " + exp);
//        System.out.println("to return: " + super.getValue(exp));
        return super.getValue(exp);
    }

    private static final Map<Object, String> PLATFORM_TO_CLASS = Map.of(
            "JBoss", "jboss-platform",
            "weblogic", "org.eclipse.persistence.platform.server.wls.WebLogic_12_Platform",
            "Glassfish", "glassfish-platform"
    );
}
