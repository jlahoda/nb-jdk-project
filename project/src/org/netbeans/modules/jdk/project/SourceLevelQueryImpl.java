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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.netbeans.spi.java.queries.SourceLevelQueryImplementation;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public class SourceLevelQueryImpl implements SourceLevelQueryImplementation  {

    private static final Logger LOG = Logger.getLogger(SourceLevelQueryImpl.class.getName());
    private static final String DEFAULT_SOURCE_LEVEL = "1.9";
    private static final Pattern JDK_PATTERN = Pattern.compile("jdk([0-9]+)");

    private final String sourceLevel;

    public SourceLevelQueryImpl(FileObject jdkRoot) {
        FileObject jcheckConf = jdkRoot.getFileObject(".jcheck/conf");
        String sl = DEFAULT_SOURCE_LEVEL;

        if (jcheckConf != null) {
            Properties props = new Properties();

            try (InputStream in = jcheckConf.getInputStream()) {
                props.load(in);
                String project = props.getProperty("project", "jdk9");
                Matcher m = JDK_PATTERN.matcher(project);

                if (m.find()) {
                    sl = m.group(1);
                }
            } catch (IOException ex) {
                LOG.log(Level.FINE, null, ex);
            }
        }

        this.sourceLevel = sl;
    }


    @Override
    public String getSourceLevel(FileObject javaFile) {
        return sourceLevel;
    }
    
}
