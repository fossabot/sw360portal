/*
 * Copyright Siemens AG, 2016-2018. Part of the SW360 Portal Project.
 *
 * SPDX-License-Identifier: EPL-1.0
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.sw360.licenseinfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.eclipse.sw360.attachments.db.AttachmentDatabaseHandler;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.common.WrappedException.WrappedTException;
import org.eclipse.sw360.datahandler.db.ComponentDatabaseHandler;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.*;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.licenseinfo.outputGenerators.DocxGenerator;
import org.eclipse.sw360.licenseinfo.outputGenerators.OutputGenerator;
import org.eclipse.sw360.licenseinfo.outputGenerators.TextGenerator;
import org.eclipse.sw360.licenseinfo.outputGenerators.XhtmlGenerator;
import org.eclipse.sw360.licenseinfo.parsers.*;
import org.eclipse.sw360.licenseinfo.util.LicenseNameWithTextUtils;

import java.net.MalformedURLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.eclipse.sw360.datahandler.common.CommonUtils.nullToEmptySet;
import static org.eclipse.sw360.datahandler.common.SW360Assert.assertNotNull;
import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;

/**
 * Implementation of the Thrift service
 */
public class LicenseInfoHandler implements LicenseInfoService.Iface {
    private static final Logger LOGGER = Logger.getLogger(LicenseInfoHandler.class);
    private static final int CACHE_TIMEOUT_MINUTES = 15;
    private static final int CACHE_MAX_ITEMS = 100;
    private static final String DEFAULT_LICENSE_INFO_HEADER_FILE="/DefaultLicenseInfoHeader.txt";
    private static final String DEFAULT_LICENSE_INFO_TEXT = loadDefaultLicenseInfoHeaderText();

    protected List<LicenseInfoParser> parsers;
    protected List<OutputGenerator<?>> outputGenerators;
    protected ComponentDatabaseHandler componentDatabaseHandler;
    protected Cache<String, List<LicenseInfoParsingResult>> licenseInfoCache;

    public LicenseInfoHandler() throws MalformedURLException {
        this(new AttachmentDatabaseHandler(DatabaseSettings.getConfiguredHttpClient(), DatabaseSettings.COUCH_DB_ATTACHMENTS),
                new ComponentDatabaseHandler(DatabaseSettings.getConfiguredHttpClient(), DatabaseSettings.COUCH_DB_DATABASE, DatabaseSettings.COUCH_DB_ATTACHMENTS));
    }

    @VisibleForTesting
    protected LicenseInfoHandler(AttachmentDatabaseHandler attachmentDatabaseHandler,
                              ComponentDatabaseHandler componentDatabaseHandler) throws MalformedURLException {
        this.componentDatabaseHandler = componentDatabaseHandler;
        this.licenseInfoCache = CacheBuilder.newBuilder().expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .maximumSize(CACHE_MAX_ITEMS).build();

        AttachmentContentProvider contentProvider = attachment -> attachmentDatabaseHandler.getAttachmentContent(attachment.getAttachmentContentId());

        // @formatter:off
        parsers = Lists.newArrayList(
            new SPDXParser(attachmentDatabaseHandler.getAttachmentConnector(), contentProvider),
            new CLIParser(attachmentDatabaseHandler.getAttachmentConnector(), contentProvider),
            new CombinedCLIParser(attachmentDatabaseHandler.getAttachmentConnector(), contentProvider, componentDatabaseHandler)
        );

        outputGenerators = Lists.newArrayList(
            new TextGenerator(),
            new XhtmlGenerator(),
            new DocxGenerator()
        );
        // @formatter:on
    }

    @Override
    public LicenseInfoFile getLicenseInfoFile(Project project, User user, String outputGeneratorClassName,
            Map<String, Set<String>> releaseIdsToSelectedAttachmentIds, Map<String, Set<LicenseNameWithText>> excludedLicensesPerAttachment)
            throws TException {
        assertNotNull(project);
        assertNotNull(user);
        assertNotNull(outputGeneratorClassName);
        assertNotNull(releaseIdsToSelectedAttachmentIds);
        assertNotNull(excludedLicensesPerAttachment);

        Map<Release, Set<String>> releaseToAttachmentId = mapKeysToReleases(releaseIdsToSelectedAttachmentIds, user);
        Collection<LicenseInfoParsingResult> projectLicenseInfoResults = getAllReleaseLicenseInfos(releaseToAttachmentId, user,
                excludedLicensesPerAttachment);

        OutputGenerator<?> generator = getOutputGeneratorByClassname(outputGeneratorClassName);
        LicenseInfoFile licenseInfoFile = new LicenseInfoFile();

        licenseInfoFile.setOutputFormatInfo(generator.getOutputFormatInfo());
        String licenseInfoHeaderText = (project.isSetLicenseInfoHeaderText()) ? project.getLicenseInfoHeaderText() : getDefaultLicenseInfoHeaderText();
        Object output = generator.generateOutputFile(projectLicenseInfoResults, project.getName(), licenseInfoHeaderText);
        if (output instanceof byte[]) {
            licenseInfoFile.setGeneratedOutput((byte[]) output);
        } else if (output instanceof String) {
            licenseInfoFile.setGeneratedOutput(((String) output).getBytes());
        } else {
            throw new TException("Unsupported output generator result: " + output.getClass().getName());
        }

        return licenseInfoFile;
    }

    @Override
    public List<OutputFormatInfo> getPossibleOutputFormats() {
        return outputGenerators.stream().map(OutputGenerator::getOutputFormatInfo).collect(Collectors.toList());
    }

    @Override
    public OutputFormatInfo getOutputFormatInfoForGeneratorClass(String generatorClassName) throws TException {
        OutputGenerator<?> generator = getOutputGeneratorByClassname(generatorClassName);
        return generator.getOutputFormatInfo();
    }

    @Override
    public List<LicenseInfoParsingResult> getLicenseInfoForAttachment(Release release, String attachmentContentId, User user)
            throws TException {
        if (release == null) {
            return Collections.singletonList(noSourceParsingResult("No release given"));
        }

        List<LicenseInfoParsingResult> cachedResults = licenseInfoCache.getIfPresent(attachmentContentId);
        if (cachedResults != null) {
            return cachedResults;
        }

        Attachment attachment = nullToEmptySet(release.getAttachments()).stream()
                .filter(a -> a.getAttachmentContentId().equals(attachmentContentId)).findFirst().orElseThrow(() -> {
                    String message = String.format(
                            "Attachment selected for license info generation is not found in release's attachments. Release id: %s. Attachment content id: %s",
                            release.getId(), attachmentContentId);
                    return new IllegalStateException(message);
                });

        try {

            List<LicenseInfoParser> applicableParsers = parsers.stream()
                    .filter(parser -> wrapTException(() -> parser.isApplicableTo(attachment, user, release))).collect(Collectors.toList());

            if (applicableParsers.size() == 0) {
                LOGGER.warn("No applicable parser has been found for the attachment selected for license information");
                return assignReleaseToLicenseInfoParsingResult(
                        assignFileNameToLicenseInfoParsingResult(
                                noSourceParsingResult("No applicable parser has been found for the attachment"), attachment.getFilename()),
                        release);
            } else if (applicableParsers.size() > 1) {
                LOGGER.info("More than one parser claims to be able to parse attachment with contend id " + attachmentContentId);
            }

            List<LicenseInfoParsingResult> results = applicableParsers.stream()
                    .map(parser -> wrapTException(() -> parser.getLicenseInfos(attachment, user, release))).flatMap(Collection::stream)
                    .collect(Collectors.toList());
            filterEmptyLicenses(results);

            results = assignReleaseToLicenseInfoParsingResults(results, release);
            licenseInfoCache.put(attachmentContentId, results);
            return results;
        } catch (WrappedTException exception) {
            throw exception.getCause();
        }
    }

    private LicenseInfoParsingResult assignFileNameToLicenseInfoParsingResult(LicenseInfoParsingResult licenseInfoParsingResult, String filename) {
        if (licenseInfoParsingResult.getLicenseInfo() == null) {
            licenseInfoParsingResult.setLicenseInfo(new LicenseInfo());
        }
        licenseInfoParsingResult.getLicenseInfo().addToFilenames(filename);
        return licenseInfoParsingResult;
    }

    @Override
    public String getDefaultLicenseInfoHeaderText() {
        return DEFAULT_LICENSE_INFO_TEXT;
    }

    protected Map<Release, Set<String>> mapKeysToReleases(Map<String, Set<String>> releaseIdsToAttachmentIds, User user) throws TException {
        Map<Release, Set<String>> result = Maps.newHashMap();
        try {
            releaseIdsToAttachmentIds
                    .forEach((relId, attIds) -> wrapTException(() -> result.put(componentDatabaseHandler.getRelease(relId, user), attIds)));
        } catch (WrappedTException exception) {
            throw exception.getCause();
        }
        return result;
    }

    protected void filterEmptyLicenses(List<LicenseInfoParsingResult> results) {
        for (LicenseInfoParsingResult result : results) {
            if (result.isSetLicenseInfo() && result.getLicenseInfo().isSetLicenseNamesWithTexts()) {
                result.getLicenseInfo().setLicenseNamesWithTexts(
                        result.getLicenseInfo().getLicenseNamesWithTexts().stream().filter(licenseNameWithText -> {
                            return !LicenseNameWithTextUtils.isEmpty(licenseNameWithText);
                        }).collect(Collectors.toSet()));
            }
        }
    }

    protected Collection<LicenseInfoParsingResult> getAllReleaseLicenseInfos(Map<Release, Set<String>> releaseToSelectedAttachmentIds,
            User user, Map<String, Set<LicenseNameWithText>> excludedLicensesPerAttachment) throws TException {
        List<LicenseInfoParsingResult> results = Lists.newArrayList();

        for (Entry<Release, Set<String>> entry : releaseToSelectedAttachmentIds.entrySet()) {
            for (String attachmentContentId : entry.getValue()) {
                if (attachmentContentId != null) {
                    Set<LicenseNameWithText> licencesToExclude = excludedLicensesPerAttachment.getOrDefault(attachmentContentId,
                            Sets.newHashSet());
                    List<LicenseInfoParsingResult> parsedLicenses = getLicenseInfoForAttachment(entry.getKey(), attachmentContentId, user);

                    results.addAll(
                            parsedLicenses.stream().map(result -> filterLicenses(result, licencesToExclude)).collect(Collectors.toList()));
                }
            }
        }

        return results;
    }

    protected LicenseInfoParsingResult filterLicenses(LicenseInfoParsingResult result, Set<LicenseNameWithText> licencesToExclude) {
        // make a deep copy to NOT change the original document that is cached
        LicenseInfoParsingResult newResult = result.deepCopy();

        if (result.getLicenseInfo() != null) {
            Set<LicenseNameWithText> filteredLicenses = nullToEmptySet(result.getLicenseInfo().getLicenseNamesWithTexts())
                    .stream()
                    .filter(license -> {
                        for (LicenseNameWithText excludeLicense : licencesToExclude) {
                            if (LicenseNameWithTextUtils.licenseNameWithTextEquals(license, excludeLicense)) {
                                return false;
                            }
                        }
                        return true;
                    }).collect(Collectors.toSet());
            newResult.getLicenseInfo().setLicenseNamesWithTexts(filteredLicenses);
        }
        return newResult;
    }

    protected LicenseInfoParsingResult noSourceParsingResult(String message) {
        return new LicenseInfoParsingResult().setStatus(LicenseInfoRequestStatus.NO_APPLICABLE_SOURCE).setMessage(message);
    }

    protected List<LicenseInfoParsingResult> assignReleaseToLicenseInfoParsingResult(LicenseInfoParsingResult licenseInfoParsingResult,
            Release release) {
        return assignReleaseToLicenseInfoParsingResults(Collections.singletonList(licenseInfoParsingResult), release);
    }

    protected List<LicenseInfoParsingResult> assignReleaseToLicenseInfoParsingResults(List<LicenseInfoParsingResult> parsingResults,
            Release release) {
        parsingResults.forEach(r -> {
            //override by given release only if the fields were not set by parser, because parser knows best
            if (!r.isSetVendor() && !r.isSetName() && !r.isSetVersion()) {
                r.setVendor(release.isSetVendor() ? release.getVendor().getShortname() : "");
                r.setName(release.getName());
                r.setVersion(release.getVersion());
            }
        });
        return parsingResults;
    }

    protected OutputGenerator<?> getOutputGeneratorByClassname(String generatorClassname) throws TException {
        assertNotNull(generatorClassname);
        return outputGenerators.stream().filter(outputGenerator -> generatorClassname.equals(outputGenerator.getClass().getName()))
                .findFirst().orElseThrow(() -> new TException("Unknown output generator: " + generatorClassname));
    }

    private static String loadDefaultLicenseInfoHeaderText(){
            String defaultLicenseInfoHeader = new String( CommonUtils.loadResource(LicenseInfoHandler.class, DEFAULT_LICENSE_INFO_HEADER_FILE).orElse(new byte[0]) );
            defaultLicenseInfoHeader = defaultLicenseInfoHeader.replaceAll("(?m)^#.*\\n", "");  // ignore comments in template file
            return defaultLicenseInfoHeader;
    }
}
