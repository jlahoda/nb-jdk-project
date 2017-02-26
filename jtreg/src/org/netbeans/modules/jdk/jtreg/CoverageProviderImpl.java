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
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 2016 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */
package org.netbeans.modules.jdk.jtreg;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.ChangeListener;

import com.sun.tdk.jcov.data.FileFormatException;
import com.sun.tdk.jcov.report.AncFilter;
import com.sun.tdk.jcov.report.ClassCoverage;
import com.sun.tdk.jcov.report.DataType;
import com.sun.tdk.jcov.report.ItemCoverage;
import com.sun.tdk.jcov.report.MethodCoverage;
import com.sun.tdk.jcov.report.PackageCoverage;
import com.sun.tdk.jcov.report.ProductCoverage;
import com.sun.tdk.jcov.report.ancfilters.CatchANCFilter;
import com.sun.tdk.jcov.report.ancfilters.DeprecatedANCFilter;
import com.sun.tdk.jcov.report.ancfilters.EmptyANCFilter;
import com.sun.tdk.jcov.report.ancfilters.SyntheticANCFilter;
import com.sun.tdk.jcov.report.ancfilters.ThrowANCFilter;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.modules.editor.coverage.spi.CoverageProvider;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class CoverageProviderImpl implements CoverageProvider {

    private final ChangeSupport cs = new ChangeSupport(this);
    private final FileObject sourceFile;
    private File  jcovFile;
    private final FileChangeListener jcovFileListener = new FileChangeListener() {
        @Override
        public void fileFolderCreated(FileEvent fe) {
        }
        @Override
        public void fileDataCreated(FileEvent fe) {
            cs.fireChange();
        }
        @Override
        public void fileChanged(FileEvent fe) {
            cs.fireChange();
        }
        @Override
        public void fileDeleted(FileEvent fe) {
            //keep the last coverage
        }
        @Override
        public void fileRenamed(FileRenameEvent fre) {
            //? ignore for now
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fae) {
        }
    };

    public CoverageProviderImpl(FileObject sourceFile) {
        this.sourceFile = sourceFile;
    }
    
    @Override
    public CoverageData getCoverage() {
        ClassPath source = ClassPath.getClassPath(sourceFile, ClassPath.SOURCE);
        String fqn = source != null ? source.getResourceName(sourceFile, '.', false) : null;

        if (fqn == null || fqn.lastIndexOf('.') == (-1)) {
            return null;
        }

        File jcovData = new File(new File(new File(org.netbeans.modules.jdk.jtreg.Utilities.jtregOutputDir(sourceFile), "jcov"), "jcov.xml"), fqn.replace(".", "/") + ".xml");

        if (!Objects.equals(jcovFile, jcovData)) {
            if (jcovFile != null)
                FileUtil.removeFileChangeListener(jcovFileListener, jcovFile);
            jcovFile = jcovData;
            FileUtil.addFileChangeListener(jcovFileListener, jcovFile);
        }

        if (!jcovData.canRead()) {
            return null;
        }

        long lastModified = jcovData.lastModified();
        String pack = fqn.substring(0, fqn.lastIndexOf('.'));
        String simpleFileName = sourceFile.getNameExt();

        ProductCoverage coverage;
        
        try {
            coverage = new ProductCoverage(jcovData.getAbsolutePath(), new AncFilter[] {
                new CatchANCFilter(),
                new DeprecatedANCFilter(),
                new EmptyANCFilter(),
                new SyntheticANCFilter(),
                new ThrowANCFilter(),
            });
        } catch (FileFormatException ex) {
            Logger.getLogger(CoverageProviderImpl.class.getName()).log(Level.FINE, null, ex);
            return new CoverageData(Collections.emptyList(), -1);
        }

        List<Coverage> result = new ArrayList<>();

        for (PackageCoverage pcov : coverage.getPackages()) {
            if (!pcov.getName().contains(pack))
                continue;
            for (ClassCoverage ccov : pcov.getClasses()) {
                if (!ccov.getSource().equals(simpleFileName))
                    continue;
                Map<Integer, List<ItemCoverage>> perLineCoverage = new TreeMap<>();
                for (MethodCoverage mcov : ccov.getMethods()) {
                    for (ItemCoverage icov : mcov.getItems()) {
                        List<ItemCoverage> items = perLineCoverage.get(icov.getSourceLine());

                        if (items == null) {
                            perLineCoverage.put(icov.getSourceLine(), items = new ArrayList<>());
                        }

                        items.add(icov);
                    }
                }

                perLineCoverage.remove(-1);
                Iterator<Entry<Integer, List<ItemCoverage>>> cov = perLineCoverage.entrySet().iterator();
                if (cov.hasNext()) {
                    Entry<Integer, List<ItemCoverage>> currentEntry = cov.next();
                    do {
                        Entry<Integer, List<ItemCoverage>> nextEntry = cov.hasNext() ? cov.next() : null;
                        int endLine = nextEntry != null ? nextEntry.getKey() - 1 : (int) ccov.getLastLine();

                        while (!ccov.isCode(endLine) && endLine > currentEntry.getKey())
                            endLine--;

                        int[] lines = new int[endLine - currentEntry.getKey() + 1];
                        int lineIndex = 0;

                        for (int l = currentEntry.getKey(); l <= endLine; l++) {
                            if (!ccov.isCode(l))
                                continue;
                            lines[lineIndex++] = l - 1; //0-based
                        }

                        lines = Arrays.copyOf(lines, lineIndex);

                        boolean covered = true;
                        boolean anc = true;
                        StringBuilder comment = new StringBuilder();

                        for (DataType type : new DataType[] {DataType.BLOCK, DataType.BRANCH}) {
                            com.sun.tdk.jcov.report.CoverageData data = getCummulativeCoverage(type, currentEntry.getValue());

                            if (data.getTotal() == 0)
                                continue;

                            covered &= data.getCovered() == data.getTotal();
                            anc &= (data.getCovered() + data.getAnc()) == data.getTotal();

                            comment.append(type.getTitle() + ": " + data.getCovered() + (data.getAnc() > 0 ? "(+" + data.getAnc() + ")" : "") + "/" + data.getTotal() + " ");
                        }

                        result.add(new Coverage(lines, covered ? CoverageType.COVERED : anc ? CoverageType.NOT_COVERED_ACCEPTABLE : CoverageType.NOT_COVERED, comment.toString()));

                        currentEntry = nextEntry;
                    } while (currentEntry != null);
                }
            }
        }

        return new CoverageData(result, lastModified);
    }

    private static com.sun.tdk.jcov.report.CoverageData getCummulativeCoverage(DataType type, Iterable<ItemCoverage> items) {
        int coveredBranch = 0;
        int ancBranch = 0;
        int totalBranch = 0;

        for (ItemCoverage ic : items) {
            if (ic.getDataType() == type) {
                com.sun.tdk.jcov.report.CoverageData data = ic.getData(type);

                if (data.getTotal() > 0) {
                    coveredBranch += data.getCovered();
                    ancBranch += data.getAnc();
                    totalBranch += data.getTotal();
                }
            }
        }

        return new com.sun.tdk.jcov.report.CoverageData(coveredBranch, ancBranch, totalBranch);
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        cs.addChangeListener(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        cs.removeChangeListener(l);
    }

    @ServiceProvider(service=Factory.class)
    public static final class FactoryImpl implements Factory {

        @Override
        public CoverageProvider createProvider(FileObject file) {
            return new CoverageProviderImpl(file);
        }

    }
}
