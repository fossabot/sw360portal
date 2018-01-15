/*
 * Copyright Bosch Software Innovations GmbH, 2016.
 * With modifications by Siemens AG, 2018.
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

import org.apache.log4j.Logger;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoParsingResult;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.OutputFormatVariant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

import static org.eclipse.sw360.licenseinfo.outputGenerators.DocxUtils.*;

public class DocxGenerator extends OutputGenerator<byte[]> {

    private static final Logger LOGGER = Logger.getLogger(TextGenerator.class);

    private static final String DOCX_TEMPLATE_FILE = "/templateFrontpageContent.docx";
    private static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_OUTPUT_TYPE = "docx";

    public DocxGenerator(OutputFormatVariant outputFormatVariant, String description) {
        super(DOCX_OUTPUT_TYPE, description, true, DOCX_MIME_TYPE, outputFormatVariant);
    }

    @Override
    public byte[] generateOutputFile(Collection<LicenseInfoParsingResult> projectLicenseInfoResults, String projectName, String licenseInfoHeaderText) throws SW360Exception {
        XWPFDocument xwpfDocument = generateDocument(projectName, licenseInfoHeaderText);
        ByteArrayOutputStream docxOutputStream = new ByteArrayOutputStream();

        switch (getOutputVariant()) {
            case REPORT:
                fillReportDocument(xwpfDocument, projectLicenseInfoResults);
                break;
            case DISCLOSURE:
                fillDisclosureDocument(xwpfDocument, projectLicenseInfoResults);
                break;
            default:
                throw new IllegalArgumentException("Unknown variant type: " + getOutputVariant());
        }

        try {
            xwpfDocument.write(docxOutputStream);
            docxOutputStream.close();
        } catch (IOException ioe) {
            LOGGER.error("Could not generate docx license info file", ioe);
            throw new SW360Exception("Got IOException when generating docx document: " + ioe.getMessage());
        }

        return docxOutputStream.toByteArray();
    }

    private XWPFDocument generateDocument(String projectName, String licenseInfoHeaderText) throws SW360Exception {
        try {
            XWPFDocument xwpfDocument = new XWPFDocument(this.getClass().getResourceAsStream(DOCX_TEMPLATE_FILE));
            cleanUpTemplate(xwpfDocument);
            setHeaderTextInDocument(xwpfDocument, licenseInfoHeaderText);
            setProjectNameInDocument(xwpfDocument, projectName);
            return xwpfDocument;
        } catch (IOException ioe) {
            LOGGER.error("Could not create docx license info file", ioe);
            throw new SW360Exception("Got IOException when creating docx document: " + ioe.getMessage());
        }
    }

    private void fillDisclosureDocument(XWPFDocument document, Collection<LicenseInfoParsingResult> projectLicenseInfoResults) {
        String[] tableHeaders = {"Name of OSS Component", "Version of OSS Component",
                "Name and Version of License (see Appendix for License Text)", "Acknowledgements", "More Information"};
        XWPFTable table = createTableAndAddReleasesTableHeaders(document, tableHeaders);
        fillClosureReleaseTable(table, projectLicenseInfoResults);

        addTextsHeader(document, "Appendix - License Texts");
        addLicenseTexts(document, projectLicenseInfoResults);
    }

    private void fillReportDocument(XWPFDocument document, Collection<LicenseInfoParsingResult> projectLicenseInfoResults) {
        String[] tableHeadersList = {"Name of OSS Component", "Version of OSS Component", "Copyright Statements"};
        XWPFTable tableList = createTableAndAddReleasesTableHeaders(document, tableHeadersList);
        fillReportReleasesTableList(tableList, projectLicenseInfoResults);

        addTextsHeader(document, "Detailed Release Information");
        String[] tableHeadersDetails = {"Component", "License", "Todos", "Acknowledgements"};
        XWPFTable tableDetails = createTableAndAddReleasesTableHeaders(document, tableHeadersDetails);
        fillReportReleasesTableDetails(tableDetails, projectLicenseInfoResults);

        addTextsHeader(document, "Appendix - License Texts");
        addLicenseTexts(document, projectLicenseInfoResults);
    }
}
