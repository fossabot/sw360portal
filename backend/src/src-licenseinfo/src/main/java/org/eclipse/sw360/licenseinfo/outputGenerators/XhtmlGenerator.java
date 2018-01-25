/*
 * Copyright Bosch Software Innovations GmbH, 2016.
 * With modifications by Siemens AG, 2017-2018.
 * Part of the SW360 Portal Project.
 *
 * SPDX-License-Identifier: EPL-1.0
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.sw360.licenseinfo.outputGenerators;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoParsingResult;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.OutputFormatVariant;

import java.util.Collection;

public class XhtmlGenerator extends OutputGenerator<String> {
    private static final Logger LOGGER = Logger.getLogger(XhtmlGenerator.class);

    private static final String XHTML_TEMPLATE_FILE = "xhtmlLicenseInfoFile.vm";
    private static final String XHTML_MIME_TYPE = "application/xhtml+xml";
    private static final String XHTML_OUTPUT_TYPE = "html";

    public XhtmlGenerator(OutputFormatVariant outputFormatVariant, String description) {
        super(XHTML_OUTPUT_TYPE, description, false, XHTML_MIME_TYPE, outputFormatVariant);
    }

    @Override
    public String generateOutputFile(Collection<LicenseInfoParsingResult> projectLicenseInfoResults, String projectName, String licenseInfoHeaderText) throws SW360Exception {
        switch (getOutputVariant()) {
            case DISCLOSURE:
                return generateDisclosure(projectLicenseInfoResults, licenseInfoHeaderText);
            default:
                throw new IllegalArgumentException("Unknown variant type: " + getOutputVariant());
        }
    }

    private String generateDisclosure(Collection<LicenseInfoParsingResult> projectLicenseInfoResults, String licenseInfoHeaderText) {
        try {
            return renderTemplateWithDefaultValues(projectLicenseInfoResults, XHTML_TEMPLATE_FILE, convertHeaderTextToHTML(licenseInfoHeaderText));
        } catch (Exception e) {
            LOGGER.error("Could not generate xhtml license info file", e);
            return "License information could not be generated.\nAn exception occured: " + e.toString();
        }
    }

    private String convertHeaderTextToHTML(String headerText) {
        String html = StringEscapeUtils.escapeHtml(headerText);
        html = html.replace("\n", "<br>");
        return html;
    }
}

