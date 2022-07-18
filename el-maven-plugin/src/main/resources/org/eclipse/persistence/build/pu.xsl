<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.

    This program and the accompanying materials are made available under the
    terms of the Eclipse Public License v. 2.0 which is available at
    http://www.eclipse.org/legal/epl-2.0,
    or the Eclipse Distribution License v. 1.0 which is available at
    http://www.eclipse.org/org/documents/edl-v10.php.

    SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause

-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns="https://jakarta.ee/xml/ns/persistence"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <xsl:output method="xml" encoding="UTF-8" indent="yes"
                xalan:indent-amount="4" xmlns:xalan="http://xml.apache.org/xalan" />
    <xsl:strip-space elements="*" />

    <xsl:param name="data-source-type" select="''"/>
    <xsl:param name="data-source-name" select="''"/>
    <xsl:param name="db.platform" select="''"/>
    <xsl:param name="server.platform" select="''"/>
    <xsl:param name="server.weaving" select="''"/>
    <xsl:param name="generator.id" select="'UNDEFINED'"/>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- strip-off comments -->
    <xsl:template match="comment()"/>

    <!-- generator marker -->
    <xsl:template match="/">
        <xsl:text>&#10;</xsl:text><xsl:comment>This file was generated by <xsl:value-of select="$generator.id"/>.</xsl:comment><xsl:text>&#10;</xsl:text>
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- process persistence-unit -->
    <xsl:template match="*[local-name() = 'persistence-unit']">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="*[local-name() = 'description']"/>
            <xsl:apply-templates select="*[local-name() = 'provider']"/>
            <xsl:element name="{$data-source-type}">
                <xsl:value-of select="$data-source-name"/>
            </xsl:element>
            <xsl:apply-templates select="*[local-name() = 'mapping-file']"/>
            <xsl:apply-templates select="*[local-name() = 'jar-file']"/>
            <xsl:apply-templates select="*[local-name() = 'class']"/>
            <xsl:apply-templates select="*[local-name() = 'exclude-unlisted-classes']"/>
            <xsl:apply-templates select="*[local-name() = 'shared-cache-mode']"/>
            <xsl:apply-templates select="*[local-name() = 'validation-mode']"/>
            <xsl:apply-templates select="*[local-name() = 'properties']"/>
        </xsl:copy>
    </xsl:template>

    <!-- process persistence-unit/properties -->
    <xsl:template match="*[local-name() = 'properties']">
        <xsl:copy>
            <xsl:if test="not(*[local-name() = 'property'][(@name='eclipselink.target-database')])">
                <xsl:element name="property" namespace="{namespace-uri()}">
                    <xsl:attribute name="name">eclipselink.target-database</xsl:attribute>
                    <xsl:attribute name="value">
                        <xsl:value-of select="$db.platform"/>
                    </xsl:attribute>
                </xsl:element>
            </xsl:if>
            <xsl:if test="not(*[local-name() = 'property'][(@name='eclipselink.target-server')])">
                <xsl:element name="property" namespace="{namespace-uri()}">
                    <xsl:attribute name="name">eclipselink.target-server</xsl:attribute>
                    <xsl:attribute name="value">
                        <xsl:value-of select="$server.platform"/>
                    </xsl:attribute>
                </xsl:element>
            </xsl:if>
            <xsl:if test="not(*[local-name() = 'property'][(@name='eclipselink.weaving')])">
                <xsl:element name="property" namespace="{namespace-uri()}">
                    <xsl:attribute name="name">eclipselink.weaving</xsl:attribute>
                    <xsl:attribute name="value">
                        <xsl:value-of select="$server.weaving"/>
                    </xsl:attribute>
                </xsl:element>
            </xsl:if>
            <xsl:apply-templates select="*[local-name() = 'property']"/>
        </xsl:copy>
    </xsl:template>

    <!-- process persistence-unit/@transaction-type:
         missing => default/none
         RESOURCE_LOCAL => JTA -->
    <xsl:template match="@transaction-type">
        <xsl:attribute name="transaction-type">JTA</xsl:attribute>
    </xsl:template>

</xsl:stylesheet>