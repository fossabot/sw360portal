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

import org.apache.poi.xwpf.usermodel.*;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfo;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoParsingResult;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoRequestStatus;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseNameWithText;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.licenses.LicenseService;
import org.eclipse.sw360.datahandler.thrift.licenses.Todo;

import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.sw360.datahandler.common.CommonUtils.nullToEmptyString;

public class DocxUtils {

    private static final int FONT_SIZE = 12;
    private static final String TODO_DEFAULT_TEXT = "todo not determined so far.";
    public static final String ALERT_COLOR = "e95850";

    private DocxUtils() {
        //only static members
    }

    public static void cleanUpTemplate(XWPFDocument document) {
        replaceText(document, "$Heading1", "");
        replaceText(document, "$Heading2", "");
    }

    public static void setProjectNameInDocument(XWPFDocument document, String projectName) {
        replaceText(document, "$projectname", projectName);
    }

    public static void setHeaderTextInDocument(XWPFDocument document, String headerText) {
        replaceText(document, "$licenseInfoHeader", headerText);
    }

    public static XWPFTable createTableAndAddReleasesTableHeaders(XWPFDocument document, String[] headers) {
        XWPFTable table = document.createTable(1, headers.length);
        styleTable(table);
        XWPFTableRow headerRow = table.getRow(0);

        for (int headerCount = 0; headerCount < headers.length; headerCount++) {
            XWPFParagraph paragraph = headerRow.getCell(headerCount).getParagraphs().get(0);
            styleTableHeaderParagraph(paragraph);

            XWPFRun run = paragraph.createRun();
            addFormattedText(run, headers[headerCount], FONT_SIZE, true);

            paragraph.setWordWrap(true);
        }
        return table;
    }

    private static void styleTable(XWPFTable table) {
        table.setRowBandSize(1);
        table.setWidth(1);
        table.setColBandSize(1);
        table.setCellMargins(1, 1, 100, 30);
    }

    public static void fillReportReleasesTableList(XWPFTable table, Collection<LicenseInfoParsingResult> projectLicenseInfoResults) {
        for (LicenseInfoParsingResult result : projectLicenseInfoResults) {
            String releaseName = nullToEmptyString(result.getName());
            String version = nullToEmptyString(result.getVersion());
            Set<String> copyrights = Collections.EMPTY_SET;
            LicenseInfo licenseInfo = result.getLicenseInfo();
            if (licenseInfo.isSetCopyrights()) {
                copyrights = licenseInfo.getCopyrights();
            }
            addReportTableListRow(table, releaseName, version, copyrights);
        }
    }

    public static void fillReportReleasesTableDetails(XWPFTable table, Collection<LicenseInfoParsingResult> projectLicenseInfoResults) {
        List<License> licenses = getLicenses();
        for (LicenseInfoParsingResult result : projectLicenseInfoResults) {
            String releaseName = nullToEmptyString(result.getName());
            String version = nullToEmptyString(result.getVersion());
            if (result.isSetLicenseInfo()) {
                LicenseInfo licenseInfo = result.getLicenseInfo();
                if (licenseInfo.isSetLicenseNamesWithTexts()) {
                    for (LicenseNameWithText licenseNameWithText : licenseInfo.getLicenseNamesWithTexts()) {
                        Set<String> acknowledgements = new HashSet<>();

                        if (licenseNameWithText.isSetAcknowledgements()) {
                            acknowledgements.add(licenseNameWithText.getAcknowledgements());
                        }
                        String licenseSPDX = licenseNameWithText.isSetLicenseSpdxId() ? licenseNameWithText.getLicenseSpdxId() : "";
                        Set<String> todos = getTodosFromLicenses(licenseSPDX, licenses);
                        addReportTableListRow(table, releaseName, version, licenseNameWithText.getLicenseName(),
                                todos, acknowledgements);
                        releaseName = "";
                        version = "";
                    }
                }
            }
        }
    }

    private static Set<String> getTodosFromLicenses(String licenseSPDX, List<License> licenses) {
        Set<String> todos = new HashSet<>();
        if (!licenseSPDX.isEmpty() && !Objects.isNull(licenses)) {
            for (License license : licenses) {
                if (licenseSPDX.equalsIgnoreCase(license.getId())) {
                    for (Todo todo : license.getTodos()) {
                        todos.add(todo.getText());
                    }
                }
            }
        }
        if (todos.isEmpty()) {
            todos.add(TODO_DEFAULT_TEXT);
        }
        return todos;
    }

    private static void addReportTableListRow(XWPFTable table, String releaseName, String version, Set<String> copyrights) {
        XWPFTableRow row = table.createRow();

        XWPFParagraph currentParagraph = row.getCell(0).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        XWPFRun currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, releaseName, FONT_SIZE);

        currentParagraph = row.getCell(1).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, version, FONT_SIZE);

        currentParagraph = row.getCell(2).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        for (String copyright : copyrights) {
            addFormattedText(currentRun, copyright, FONT_SIZE);
            addNewLines(currentRun, 1);
        }
    }

    private static void addReportTableListRow(XWPFTable table,
                                              String releaseName,
                                              String version,
                                              String licenseName,
                                              Set<String> todos,
                                              Set<String> acknowledgements) {

        XWPFTableRow row = table.createRow();
        String releaseIdentifier = "";

        XWPFParagraph currentParagraph = row.getCell(0).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        XWPFRun currentRun = currentParagraph.createRun();
        if (!releaseName.isEmpty() && !version.isEmpty()) {
            releaseIdentifier = new StringBuilder().
                    append(releaseName).append("\r\n").append(version).toString();
        }
        addFormattedText(currentRun, releaseIdentifier, FONT_SIZE);

        currentParagraph = row.getCell(1).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, licenseName, FONT_SIZE);

        currentParagraph = row.getCell(2).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        for (String todo : todos) {
            addFormattedText(currentRun, todo, FONT_SIZE);
            addNewLines(currentRun, 1);
        }

        currentParagraph = row.getCell(3).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        for (String acknowledgement : acknowledgements) {
            addFormattedText(currentRun, acknowledgement, FONT_SIZE);
            addNewLines(currentRun, 1);
        }
    }

    public static void fillClosureReleaseTable(XWPFTable table, Collection<LicenseInfoParsingResult> projectLicenseInfoResults) {

        for (LicenseInfoParsingResult result : projectLicenseInfoResults) {
            String releaseName = nullToEmptyString(result.getName());
            String version = nullToEmptyString(result.getVersion());
            if (result.getStatus() == LicenseInfoRequestStatus.SUCCESS) {
                Set<String> copyrights = Collections.emptySet();
                Set<LicenseNameWithText> licenseNamesWithTexts = Collections.emptySet();
                Set<String> acknowledgements = Collections.emptySet();
                if (result.isSetLicenseInfo()) {
                    LicenseInfo licenseInfo = result.getLicenseInfo();
                    if (licenseInfo.isSetCopyrights()) {
                        copyrights = licenseInfo.getCopyrights();
                    }
                    if (licenseInfo.isSetLicenseNamesWithTexts()) {
                        licenseNamesWithTexts = licenseInfo.getLicenseNamesWithTexts();
                        acknowledgements = licenseNamesWithTexts.stream()
                                .map(LicenseNameWithText::getAcknowledgements)
                                .filter(Objects::nonNull).collect(Collectors.toSet());
                    }
                }
                addClosureReleaseTableRow(table, releaseName, version, licenseNamesWithTexts, acknowledgements, copyrights);
            } else {
                String filename = Optional.ofNullable(result.getLicenseInfo())
                        .map(LicenseInfo::getFilenames)
                        .map(l -> l.stream().findFirst().orElse(null))
                        .orElse("");
                addClosureReleaseTableErrorRow(table, releaseName, version, nullToEmptyString(result.getMessage()), filename);
            }
        }
    }

    private static void addClosureReleaseTableRow(XWPFTable table, String releaseName, String version, Set<LicenseNameWithText> licenseNamesWithTexts, Set<String> acknowledgements, Set<String> copyrights) {
        XWPFTableRow row = table.createRow();

        XWPFParagraph currentParagraph = row.getCell(0).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        XWPFRun currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, releaseName, FONT_SIZE);

        currentParagraph = row.getCell(1).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, version, FONT_SIZE);

        currentParagraph = row.getCell(2).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        for (LicenseNameWithText licenseNameWithText : licenseNamesWithTexts) {
            String licenseName = licenseNameWithText.isSetLicenseName()
                    ? licenseNameWithText.getLicenseName()
                    : "Unknown license name";
            addFormattedText(currentRun, licenseName, FONT_SIZE);
            addNewLines(currentRun, 1);
        }

        currentParagraph = row.getCell(3).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        for (String ack : acknowledgements) {
            addFormattedText(currentRun, ack, FONT_SIZE);
            addNewLines(currentRun, 1);
        }
        currentParagraph = row.getCell(4).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        for (String copyright : copyrights) {
            addFormattedText(currentRun, copyright, FONT_SIZE);
            addNewLines(currentRun, 1);
        }
    }

    private static List<License> getLicenses() {
        LicenseService.Iface licenseClient = new ThriftClients().makeLicenseClient();
        try {
            return licenseClient.getLicenses();
        } catch (TException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void addClosureReleaseTableErrorRow(XWPFTable table, String releaseName, String version, String error, String filename) {
        XWPFTableRow row = table.createRow();

        XWPFParagraph currentParagraph = row.getCell(0).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        XWPFRun currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, releaseName, FONT_SIZE);

        currentParagraph = row.getCell(1).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, version, FONT_SIZE);

        currentParagraph = row.getCell(2).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, String.format("Error reading license information: %s", error), FONT_SIZE, false, ALERT_COLOR);

        currentParagraph = row.getCell(4).getParagraphs().get(0);
        styleTableHeaderParagraph(currentParagraph);
        currentRun = currentParagraph.createRun();
        addFormattedText(currentRun, String.format("Source file: %s", filename), FONT_SIZE, false, ALERT_COLOR);
    }

    private static void styleTableHeaderParagraph(XWPFParagraph paragraph) {
        paragraph.setIndentationLeft(0);
        paragraph.setWordWrap(true);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
    }

    public static void addTextsHeader(XWPFDocument document, String header) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        addPageBreak(run);
        XWPFParagraph textParagraph = document.createParagraph();
        XWPFRun textRun = textParagraph.createRun();
        textParagraph.setStyle("Heading1");
        textRun.setFontSize(FONT_SIZE + 4);
        textRun.setText(header);

        addNewLines(textRun, 1);
    }

    public static void addLicenseTexts(XWPFDocument document, Collection<LicenseInfoParsingResult> projectLicenseInfoResults) {
        List<LicenseNameWithText> lts = OutputGenerator.getSortedLicenseNameWithTexts(projectLicenseInfoResults);

        lts.forEach(lt -> {
            XWPFParagraph licenseParagraph = document.createParagraph();
            licenseParagraph.setStyle("Heading2");
            XWPFRun licenseRun = licenseParagraph.createRun();
            String licenseName = lt.isSetLicenseName() ? lt.getLicenseName() : "Unknown license name";
            licenseRun.setText(licenseName);
            addNewLines(licenseRun, 1);

            XWPFParagraph licenseTextParagraph = document.createParagraph();
            XWPFRun licenseTextRun = licenseTextParagraph.createRun();
            addFormattedText(licenseTextRun, nullToEmptyString(lt.getLicenseText()), FONT_SIZE);
            addNewLines(licenseTextRun, 1);
        });
    }

    private static void addNewLines(XWPFRun run, int numberOfNewlines) {
        for (int count = 0; count < numberOfNewlines; count++) {
            run.addCarriageReturn();
            run.addBreak(BreakType.TEXT_WRAPPING);
        }
    }

    private static void addPageBreak(XWPFRun run) {
        run.addBreak(BreakType.TEXT_WRAPPING);
        run.addBreak(BreakType.PAGE);
    }

    /**
     * Adds the given text in a run object, properly formatting \n as line break
     */
    private static void setText(XWPFRun run, String text) {
        String[] split = text.split("\n");
        run.setText(split[0]);
        for (int i = 1; i < split.length; i++) {
            run.addBreak();
            run.setText(split[i]);
        }
    }

    private static void addFormattedText(XWPFRun run, String text, String fontFamily, int fontSize, boolean bold, String rrggbbColor) {
        run.setFontSize(fontSize);
        run.setFontFamily(fontFamily);
        run.setBold(bold);
        if (rrggbbColor != null) {
            run.setColor(rrggbbColor);
        }
        setText(run, text);
    }

    private static void addFormattedText(XWPFRun run, String text, int fontSize, boolean bold, String rrggbbColor) {
        addFormattedText(run, text, "Calibri", fontSize, bold, rrggbbColor);
    }

    private static void addFormattedText(XWPFRun run, String text, int fontSize, boolean bold) {
        addFormattedText(run, text, "Calibri", fontSize, bold, null);
    }

    private static void addFormattedText(XWPFRun run, String text, int fontSize) {
        addFormattedText(run, text, fontSize, false);
    }

    private static void replaceText(XWPFDocument document, String placeHolder, String replaceText) {
        for (XWPFHeader header : document.getHeaderList())
            replaceAllBodyElements(header.getBodyElements(), placeHolder, replaceText);
        replaceAllBodyElements(document.getBodyElements(), placeHolder, replaceText);
    }

    private static void replaceAllBodyElements(List<IBodyElement> bodyElements, String placeHolder, String replaceText) {
        for (IBodyElement bodyElement : bodyElements) {
            if (bodyElement.getElementType().compareTo(BodyElementType.PARAGRAPH) == 0)
                replaceParagraph((XWPFParagraph) bodyElement, placeHolder, replaceText);
        }
    }

    private static void replaceParagraph(XWPFParagraph paragraph, String placeHolder, String replaceText) {
        for (XWPFRun r : paragraph.getRuns()) {
            String text = r.getText(r.getTextPosition());
            if (text != null && text.contains(placeHolder)) {
                text = text.replace(placeHolder, replaceText);
                r.setText(text, 0);
            }
        }
    }
}
