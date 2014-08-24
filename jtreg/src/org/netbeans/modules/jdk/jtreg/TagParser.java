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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.ClassPath.PathConversionMode;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class TagParser {

//    private static final Pattern TAG_PATTERN = Pattern.compile("@([A-Za-z]+)(/[^\n]*) (.*)\n");
//    private static final Pattern RUN_TAG_PATTERN = Pattern.compile("([A-Za-z]+)(/[^\n]*) (.*)\n");
    private static final Pattern TAG_PATTERN = Pattern.compile("@([A-Za-z]+)(/[^\\n ]*)?( +(.*))?\n");
    private static final Pattern RUN_TAG_PATTERN = Pattern.compile("([A-Za-z]+)(/[^\\n ]*)?( +(.*))?");

    private final FileObject source;
    private final File srcDir;
    private final File targetDir;
    private final File testScratch;
    private final ClassPath sourcePath;
    private final ClassPath compilePath;

    public TagParser(FileObject source) {
        this.source = source;

        FileObject searchRoot = source;

        while (!Utilities.isJDKRepository(searchRoot))
            searchRoot = searchRoot.getParent();

        this.srcDir = FileUtil.toFile(searchRoot);
        this.targetDir = new File(this.srcDir, "build/classes");
        this.testScratch = new File(this.srcDir, "build/nb-test-scratch");

        sourcePath = ClassPath.getClassPath(source, ClassPath.SOURCE);
        compilePath = ClassPath.getClassPath(source, ClassPath.COMPILE);
    }

    public String translate(CharSequence seq) {
        Matcher m = TAG_PATTERN.matcher(seq);
        boolean isTest = false;
        StringBuilder result = new StringBuilder();

        result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        result.append("<project name='" + source.getName() + "' basedir='.'>\n");
        result.append("<delete dir='" + testScratch.getAbsolutePath() + "' />\n");
        result.append("<mkdir dir='" + testScratch.getAbsolutePath() + "' />\n");
        if (new File(srcDir, "src/share/classes/com/sun/tools/javac/Main.java").canRead()) {
            //langtools:
            result.append("<ant antfile='" + /*XXX: */srcDir.getAbsolutePath() + "/make/build.xml' useNativeBasedir='true' target='build' />\n");
        }
        result.append("<taskdef resource='testngtasks' classpath='" + compileCP() + "'/>\n");

        boolean wasActiveTag = false;

        while (m.find()) {
            String tag = m.group(1);

            if ("test".equals(tag)) {
                isTest = true;
                continue;
            }

            if (!isTest) {
                continue;
            }
            
            String options = m.group(2);
            String parameters = m.group(4);

            if (tag.equals("run")) {
                Matcher inner = RUN_TAG_PATTERN.matcher(parameters);
                
                if (!inner.find()) { //?
                    continue;
                }
                
                tag = inner.group(1);
                options = inner.group(2);
                parameters = inner.group(4);
            }

            //TODO: parse options

            switch (tag) {
                case "compile": {
                    generateCompileTask(parameters, "", true, result);
                    wasActiveTag = true;
                    break;
                }
                case "build": {
                    generateCompileTask(parameters, ".java", false, result);
                    wasActiveTag = true;
                    break;
                }
                case "main": {
                    generateCompileTask(source.getNameExt(), "", false, result);
                    generateRunMainTask(parameters, true, result);
                    wasActiveTag = true;
                    break;
                }
                case "testng": {
                    generateCompileTask(source.getNameExt(), "", false, result);
                    generateTestNGTask(parameters, true, result);
                    wasActiveTag = true;
                    break;
                }
            }
        }

        if (!wasActiveTag) {
            generateCompileTask(source.getNameExt(), "", true, result);
            generateRunMainTask(source.getName(), true, result);
        }

        result.append("</project>\n");

        return isTest ? result.toString() : null;
    }

    private int debugCount;
    private String generateStartDebugger(StringBuilder result) {
        String debugAddressProperty = "jpda.address" + debugCount++;
        result.append("<nbjpdastart name='" + source.getName() + "' addressproperty='" + debugAddressProperty + "' transport='dt_socket'>\n");
        result.append("<bootclasspath>\n");
        result.append("    <pathelement location='" + targetDir.getAbsolutePath() + "' />\n");
        result.append("    <pathelement location='${target.java.home}/jre/lib/rt.jar' />\n");
        result.append("</bootclasspath>\n");
        result.append("<sourcepath>\n");
        result.append("<pathelement location='" + srcDir.getAbsolutePath() + "' />\n");
        result.append("<pathelement path='" + sourcePath.toString(PathConversionMode.SKIP) + "' />\n");
        result.append("</sourcepath>\n");
        result.append("</nbjpdastart>\n");
        return debugAddressProperty;
    }

    private void generateCompileTask(String parameters, String paramAppend, boolean debug, StringBuilder result) {
        String debugAddressProperty = null;
        if (debug) {
            debugAddressProperty = generateStartDebugger(result);
        }
        result.append("<java classname='com.sun.tools.javac.Main' fork='true'>\n");
        result.append("    <jvmarg line='-Xbootclasspath/p:" + targetDir.getAbsolutePath() + "'/>\n");
        if (debugAddressProperty != null) {
            result.append("    <jvmarg line='-Xdebug -Xnoagent -Djava.compiler=none -Xrunjdwp:transport=dt_socket,address=${" + debugAddressProperty + "}'/>\n");
        }
        for (String param : parameters.split(" ")) {
            if (param.trim().isEmpty()) continue; //XXX
            param += paramAppend;
            if (param.endsWith(".java")) {
                FileObject sourceFile = sourcePath.findResource(param);

                if (sourceFile != null) {
                    result.append("    <arg value='" + FileUtil.toFile(sourceFile).getAbsolutePath() + "' />\n");
                }
            } else {
                result.append("    <arg value='" + param + "' />\n");
            }
        }
        result.append("    <arg value='-d' />\n");
        result.append("    <arg value='" + testScratch.getAbsolutePath() + "' />\n");
        result.append("    <arg value='-classpath' />\n");
        result.append("    <arg value='" + compileCP() + "' />\n");
        result.append("    <arg value='-g' />\n");
        result.append("</java>\n");
    }

    private String compileCP() {
        StringBuilder classpath = new StringBuilder();

        classpath.append(testScratch.getAbsolutePath());

        for (FileObject p : compilePath.getRoots()) {
            classpath.append(File.pathSeparator);
            classpath.append(FileUtil.archiveOrDirForURL(p.toURL()).getAbsolutePath());
        }

        return classpath.toString();
    }

    private void generateRunMainTask(String parameters, boolean debug, StringBuilder result) {
        String debugAddressProperty = null;
        if (debug) {
            debugAddressProperty = generateStartDebugger(result);
        }
        List<String> paramsSplit = new ArrayList<>(Arrays.asList(parameters.split(" ")));
        String mainClass = paramsSplit.remove(0);
        result.append("<java classname='" + mainClass + "' fork='true' dir='" + testScratch.getAbsolutePath() + "'>\n");
        result.append("    <jvmarg line='-Xbootclasspath/p:" + targetDir.getAbsolutePath() + "'/>\n");
        result.append("    <jvmarg line='-classpath " + compileCP() + "'/>\n");
        if (debugAddressProperty != null) {
            result.append("    <jvmarg line='-Xdebug -Xnoagent -Djava.compiler=none -Xrunjdwp:transport=dt_socket,address=${" + debugAddressProperty + "}'/>\n");
        }
        for (String param : paramsSplit) {
            if (param.trim().isEmpty()) continue; //XXX
            result.append("    <arg value='" + param + "' />\n");
        }
        result.append("</java>\n");
    }

    private void generateTestNGTask(String parameters, boolean debug, StringBuilder result) {
        String debugAddressProperty = null;
        if (debug) {
            debugAddressProperty = generateStartDebugger(result);
        }
        result.append("<testng classpath='" + compileCP() + "' outputdir='" + new File(testScratch.getAbsolutePath(), "test-output") + "'>\n");
        result.append("    <jvmarg line='-Xbootclasspath/p:" + targetDir.getAbsolutePath() + "'/>\n");
        if (debugAddressProperty != null) {
            result.append("    <jvmarg line='-Xdebug -Xnoagent -Djava.compiler=none -Xrunjdwp:transport=dt_socket,address=${" + debugAddressProperty + "}'/>\n");
        }
        result.append("<classfileset dir='" + testScratch.getAbsolutePath() + "'>\n");
        for (String clazz : parameters.split(" ")) {
            if (clazz.trim().isEmpty()) continue;
            result.append("<include name='" + clazz.replace('.', '/') + ".class' />\n");
        }
        result.append("</classfileset>\n");
        result.append("</testng>\n");
    }
}
