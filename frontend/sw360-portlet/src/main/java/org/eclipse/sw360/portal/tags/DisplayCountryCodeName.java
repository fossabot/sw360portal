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
import javax.servlet.jsp.tagext.SimpleTagSupport;
import java.io.IOException;

public class DisplayCountryCodeName extends SimpleTagSupport {

    private String value;

    public void setValue(String value) {
        this.value = value;
    }

    public void doTag() throws JspException, IOException {
        String output = "";

        CountryCode countryCode = CountryCode.getByCode(value);
        if (countryCode != null) {
            output = countryCode.getName();
        }

        getJspContext().getOut().print(output);
    }
}
