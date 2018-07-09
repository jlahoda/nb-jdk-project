/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.spi.project.ProjectConfigurationProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.ChangeSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author lahvac
 */
public class RemotePlatformImpl {//implements RemotePlatform {

    private static final Map<FileObject, Object/*should be: RemotePlatformImpl*/> jdkRoot2Platform = new HashMap<>();

    public static @NonNull Object/*should be: RemotePlatformImpl*/ getProvider(FileObject jdkRoot, ConfigurationImpl.ProviderImpl configurations) {
        return jdkRoot2Platform.computeIfAbsent(jdkRoot, r -> {
            ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
            if (cl == null)
                return null;
            try {
                Class<?> remotePlatform = cl.loadClass("org.netbeans.modules.java.source.remote.spi.RemotePlatform");
                RemotePlatformImpl delegate = new RemotePlatformImpl(configurations);
                return Proxy.newProxyInstance(cl,
                                       new Class<?>[] {remotePlatform},
                                       (inst, meth, params) -> {
                    return delegate.getClass()
                                   .getMethod(meth.getName(), meth.getParameterTypes())
                                   .invoke(delegate, params);
                });
            } catch (ClassNotFoundException ex) {
                //OK:
                return null;
            }
        });
    }

    private final ChangeSupport cs = new ChangeSupport(this);
    private final ConfigurationImpl.ProviderImpl configurations;

    public RemotePlatformImpl(ConfigurationImpl.ProviderImpl configurations) {
        this.configurations = configurations;
        this.configurations.addPropertyChangeListener(new PropertyChangeListener() {
            @Override public void propertyChange(PropertyChangeEvent evt) {
                if (ProjectConfigurationProvider.PROP_CONFIGURATION_ACTIVE.equals(evt.getPropertyName()) ||
                    evt.getPropertyName() == null) {
                    cs.fireChange();
                }
            }
        });
    }

//    @Override
    public String getJavaCommand() {
        return new File(configurations.getActiveConfiguration().getLocation(),
                        "images/jdk/bin/java".replace("/", System.getProperty("file.separator")))
               .getAbsolutePath();
    }

//    @Override
    public List<String> getJavaArguments() {
        return Collections.emptyList();
    }

//    @Override
    public void addChangeListener(ChangeListener l) {
        cs.addChangeListener(l);
    }

//    @Override
    public void removeChangeListener(ChangeListener l) {
        cs.removeChangeListener(l);
    }

    public static Lookup/*should be: RemotePlatform.Provider*/ createProvider(FileObject jdkRoot, JDKProject project) {
        Object/*should be: RemotePlatformImpl*/ platform = RemotePlatformImpl.getProvider(jdkRoot, project.configurations);
        ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
        if (cl == null)
            return Lookup.EMPTY;
        try {
            Class<?> remotePlatformProvider = cl.loadClass("org.netbeans.modules.java.source.remote.spi.RemotePlatform$Provider");
            return Lookups.singleton(Proxy.newProxyInstance(cl,
                                   new Class<?>[] {remotePlatformProvider},
                                   (inst, meth, params) -> {
                return meth.getName().equals("findPlatform") ? platform : null;
            }));
        } catch (ClassNotFoundException ex) {
            return Lookup.EMPTY;
        }
    }

}
