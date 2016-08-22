/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2016 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2016 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.project.bridge.cnd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.openide.modules.ModuleInstall;

public class Installer extends ModuleInstall {

    @Override
    public void validate() throws IllegalStateException {
        addFriend("org.netbeans.modules.cnd.api.model", "org.netbeans.modules.jdk.project.bridge.cnd");
        addFriend("org.netbeans.modules.cnd.api.project", "org.netbeans.modules.jdk.project.bridge.cnd");
        addFriend("org.netbeans.modules.cnd.model.services", "org.netbeans.modules.jdk.project.bridge.cnd");
        addFriend("org.netbeans.modules.cnd.utils", "org.netbeans.modules.jdk.project.bridge.cnd");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addFriend(String to, String who) {
        try {
            java.lang.Class main = java.lang.Class.forName("org.netbeans.core.startup.Main", false,  //NOI18N
                    Thread.currentThread().getContextClassLoader());
            Method getModuleSystem = main.getMethod("getModuleSystem", new Class[0]); //NOI18N
            Object moduleSystem = getModuleSystem.invoke(null, new Object[0]);
            Method getManager = moduleSystem.getClass().getMethod("getManager", new Class[0]); //NOI18N
            Object moduleManager = getManager.invoke(moduleSystem, new Object[0]);
            Method moduleMeth = moduleManager.getClass().getMethod("get", new Class[] {String.class}); //NOI18N
            Object module = moduleMeth.invoke(moduleManager, to); //NOI18N
            if (module != null) {
                Method moduleDataMeth = module.getClass().getSuperclass().getDeclaredMethod("data"); //NOI18N
                moduleDataMeth.setAccessible(true);
                Object moduleData = moduleDataMeth.invoke(module); //NOI18N
                Field frField = moduleData.getClass().getSuperclass().getDeclaredField("friendNames"); //NOI18N
                frField.setAccessible(true);
                Set friends = (Set)frField.get(moduleData);
                friends.add(who); //NOI18N
            }
        } catch (Exception ex) {
            new IllegalStateException("Cannot fix dependencies for " + who + ".", ex); //NOI18N
        }
        super.validate();
    }

}
