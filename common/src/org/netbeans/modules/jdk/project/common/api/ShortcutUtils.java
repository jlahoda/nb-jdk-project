/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2015 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.project.common.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public class ShortcutUtils {

    private static final Logger LOG = Logger.getLogger(ShortcutUtils.class.getName());
    private static ShortcutUtils INSTANCE;

    public static synchronized ShortcutUtils getDefault() {
        if (INSTANCE == null) {
            INSTANCE = new ShortcutUtils();
        }

        return INSTANCE;
    }

    private final ResourceBundle data;

    private ShortcutUtils() {
        ResourceBundle data;
        try (InputStream in = ShortcutUtils.class.getResourceAsStream("shortcut.properties")) {
            data = new PropertyResourceBundle(in);
        } catch (IOException ex) {
            LOG.log(Level.FINE, null, ex);
            data = new ResourceBundle() {
                @Override protected Object handleGetObject(String key) {
                    return null;
                }
                @Override public Enumeration<String> getKeys() {
                    return Collections.emptyEnumeration();
                }
            };
        }
        this.data = data;
    }

    private static final Set<String> LANGTOOLS_MODULES =
            new HashSet<>(Arrays.asList("java.compiler", "jdk.compiler",
                                        "jdk.javadoc", "jdk.jdeps", "jdk.jshell"));
    public String inferLegacyRepository(Project prj) {
        if (LANGTOOLS_MODULES.contains(prj.getProjectDirectory().getNameExt()))
            return "langtools";
        return "unknown";
    }

    public boolean shouldUseCustomBuild(String repoName, String pathInRepo) {
        return matches(repoName, pathInRepo, "project");
    }

    public boolean shouldUseCustomTest(String repoName, String pathInRepo) {
        return matches(repoName, pathInRepo, "test");
    }

    private boolean matches(String repoName, String pathInRepo, String key) {
        String include = null;
        String exclude = null;
        try {
            include = data.getString(repoName + "_include_" + key);
            exclude = data.getString(repoName + "_exclude_" + key);
        } catch (MissingResourceException ex) {
            LOG.log(Level.FINE, null, ex);
        }

        if (include == null || exclude == null)
            return false;

        try {
            return  Pattern.matches(include, pathInRepo) &&
                   !Pattern.matches(exclude, pathInRepo);
        } catch (PatternSyntaxException ex) {
            LOG.log(Level.FINE, null, ex);
            return false;
        }
    }
}
