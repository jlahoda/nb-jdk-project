/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2014 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2014 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.jtreg;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.apache.tools.ant.module.api.AntProjectCookie;
import org.apache.tools.ant.module.api.AntTargetExecutor;
import org.apache.tools.ant.module.api.AntTargetExecutor.Env;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.spi.project.ActionProvider;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.Lookup;
import org.openide.util.Task;
import org.openide.util.TaskListener;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=ActionProvider.class)
public class ActionProviderImpl implements ActionProvider {

    private static final String[] ACTIONS = new String[] {
        COMMAND_DEBUG_TEST_SINGLE,
    };

    @Override
    public String[] getSupportedActions() {
        return ACTIONS;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        try {
            FileObject file = context.lookup(FileObject.class);
            EditorCookie ec = file.getLookup().lookup(EditorCookie.class);
            StyledDocument doc = ec.getDocument();
            String content;

            if (doc != null) {
                content = doc.getText(0, doc.getLength()); //XXX: synchronization
            } else {
                content = file.asText(FileEncodingQuery.getEncoding(file).name());
            }

            String buildScript = new TagParser(file).translate(content);
            File builds = Places.getCacheSubdirectory("jtreg-support");
            File buildFile;
            int i = 0;
            while ((buildFile = new File(builds, "build" + (i > 0 ? i : "") + ".xml")).exists())
                i++;
            final FileObject tempBuildScript = FileUtil.createData(buildFile);

            try (OutputStream out = tempBuildScript.getOutputStream()) {
                out.write(buildScript.getBytes("UTF-8"));
            }

            AntProjectCookie apc = tempBuildScript.getLookup().lookup(AntProjectCookie.class);

            AntTargetExecutor.createTargetExecutor(new Env()).execute(apc, new String[0]).addTaskListener(new TaskListener() {
                @Override
                public void taskFinished(Task task) {
//                    try {
//                        tempBuildScript.delete();
//                    } catch (IOException ex) {
//                        Exceptions.printStackTrace(ex);
//                    }
                }
            });
        } catch (BadLocationException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
        FileObject file = context.lookup(FileObject.class);

        if (file == null)
            return false;
        
        while (!file.isRoot()) {
            if (file.getFileObject("src/share/classes/com/sun/tools/javac/main/Main.java") != null)
                return true;
            file = file.getParent();
        }

        return false;
    }

}
