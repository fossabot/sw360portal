/*
 * Copyright Siemens AG, 2018. Part of the SW360 Portal Project.
 *
 * SPDX-License-Identifier: EPL-1.0
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.sw360.portal.tags;

import com.neovisionaries.i18n.CountryCode;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DisplayCountryCodeSelection extends SimpleTagSupport {

    private String selected;
    private String preferredCountryCodes;

    public void setSelected(String selected) {
        this.selected = selected;
    }

    public void setPreferredCountryCodes(String preferredCountryCodes) {
        this.preferredCountryCodes = preferredCountryCodes;
    }

    public void doTag() throws JspException, IOException {
        writeOptions(selected, preferredCountryCodes);
    }

    private void writeOptions(String selected, String preferredCountryCodes) throws IOException {
        JspWriter jspWriter = getJspContext().getOut();

        if (selected == null || selected.isEmpty()) {
            jspWriter.write("<option value=\"\">Select a country</option>");
        }

        List<CountryCode> preferredList = applyCountryCodes(preferredCountryCodes);
        if (!preferredList.isEmpty()) {
            for (CountryCode countryCode : preferredList) {
                writeOption(jspWriter, countryCode);
            }
            jspWriter.write("<option disabled>───────────────</option>");
        }

        for (CountryCode countryCode : CountryCode.values()) {
            if (!preferredList.contains(countryCode) && countryCode != CountryCode.UNDEFINED) {
                writeOption(jspWriter, countryCode);
            }
        }
    }

    private void writeOption(JspWriter jspWriter, CountryCode countryCode) throws IOException {
        boolean selected = countryCode.getAlpha2().equalsIgnoreCase(this.selected);
        jspWriter.write(String.format(
                "<option value=\"%s\" class=\"textlabel stackedLabel\" " +
                        (selected ? "selected=\"selected\" " : "") + ">%s</option>",
                countryCode, countryCode.getName()));
    }

    private List<CountryCode> applyCountryCodes(String countryCodes) {
        List<CountryCode> result = new ArrayList<>();
        if (countryCodes != null && !countryCodes.isEmpty()) {
            for (String countryCode : countryCodes.split(",")) {
                CountryCode cc = CountryCode.getByCode(countryCode, false);
                if (cc != null) {
                    result.add(cc);
                }
            }
        }
        return result;
    }
}
