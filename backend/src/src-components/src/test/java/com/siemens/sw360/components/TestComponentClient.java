/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License Version 2.0 as published by the
 * Free Software Foundation with classpath exception.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License version 2.0 for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program (please see the COPYING file); if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */
package com.siemens.sw360.components;

import com.siemens.sw360.datahandler.thrift.components.ComponentService;
import com.siemens.sw360.datahandler.thrift.users.User;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.THttpClient;

import java.io.IOException;

/**
 * Created by bodet on 10/12/14.
 *
 * @author cedric.bodet@tngtech.com
 */
public class TestComponentClient {

    private static final User user = new User().setEmail("cedric.bodet@tngtech.com").setDepartment("AB CD EF");

    public static void main(String[] args) throws TException, IOException {
        THttpClient thriftClient = new THttpClient("http://127.0.0.1:8085/components/thrift");
        TProtocol protocol = new TCompactProtocol(thriftClient);
        ComponentService.Iface client = new ComponentService.Client(protocol);

//        List<Component> components = client.getComponentSummary(user);
//        List<Component> recentComponents = client.getRecentComponents();
//        List<Release> releases = client.getReleaseSummary(user);
//
//        System.out.println("Fetched " + components.size() + " components from license service");
//        System.out.println("Fetched " + releases.size() + " releases from license service");
//        System.out.println("Fetched " + recentComponents.size() + " recent components from license service");
//
//        String referenceId =null;
//        for (Component component : recentComponents) {
//            if(referenceId==null) referenceId=component.getId();
//            System.out.println(component.getId() + ": " + component.getName());
//        }
//
//        if(referenceId!=null) {
//            System.out.println(client.getComponentById(referenceId, user).toString());
//            Component component = new ComponentHandler("http://localhost:5984", "sw360db", "sw360attachments").getComponentById(referenceId, user);
//            System.out.println(component.toString());
//            System.out.println(client.getComponentById(referenceId, user).toString());
//        }
//
//        for(Release release : releases) {
//                System.out.println(release.toString());
//            }
//        // This fails with a thrift error... debug!
//        if(releases.size()>0) {
//            String releaseNameStart = releases.get(0).getName().substring(0,1);
//            System.out.println("The following releases start with " + releaseNameStart );
//
//            List<Release> releases1 = client.searchReleaseByName(releaseNameStart);
//            for(Release release : releases1) {
//                System.out.println(release.toString());
//            }
//
//        }


//        final Component cpe = client.getComponentForReportFromCPEId("cpe");

//        System.out.println(cpe.toString());

    }
}
