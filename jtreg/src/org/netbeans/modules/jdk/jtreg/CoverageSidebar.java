/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2009 Sun
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

import org.netbeans.editor.*;
import org.netbeans.editor.Utilities;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.api.editor.fold.FoldHierarchyListener;
import org.netbeans.api.editor.fold.FoldHierarchyEvent;
import org.openide.loaders.DataObject;
import org.openide.filesystems.*;
import org.openide.util.RequestProcessor;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.*;

import java.awt.event.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.plaf.TextUI;

import com.sun.tdk.jcov.data.FileFormatException;
import com.sun.tdk.jcov.report.AncFilter;
import com.sun.tdk.jcov.report.ClassCoverage;
import com.sun.tdk.jcov.report.CoverageData;
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
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.LineCookie;
import org.openide.text.Line;
import org.openide.util.Mutex;

/**
 * Left editor sidebar showing changes in the file against the base version.
 * 
 * @author Maros Sandor
 */
public class CoverageSidebar extends JPanel implements DocumentListener, ComponentListener, FoldHierarchyListener, FileChangeListener {
    
    private static final int BAR_WIDTH = 9;
    private static final Logger LOG = Logger.getLogger(CoverageSidebar.class.getName());
    private static final RequestProcessor WORKER = new RequestProcessor(CoverageSidebar.class.getName(), 1, false, false);
    
    private final JTextComponent  textComponent;
    /**
     * We must keep FileObject here because a File may change if the FileObject is renamed.
     * The fileObejct can be DELETED TOO!
     */
    private FileObject            fileObject;

    private final FoldHierarchy   foldHierarchy;
    private final BaseDocument    document;
    
    private boolean                 sidebarVisible = true;
    private boolean                 sidebarTemporarilyDisabled; // flag disallowing the sidebar to ask for file's content
    private boolean                 sidebarInComponentHierarchy;
    private long                    currentRawCoverageTimestamp;
    private LineCoverage[]          currentRawCoverage;
    private LineCoverage[]          currentCoverage;
    private long                    currentFileTimestamp;
    private File                    jcovFile;
    private final FileChangeListener      jcovFileListener = new FileChangeListener() {
        @Override
        public void fileFolderCreated(FileEvent fe) {
        }
        @Override
        public void fileDataCreated(FileEvent fe) {
            refreshCoverage();
        }
        @Override
        public void fileChanged(FileEvent fe) {
            refreshCoverage();
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

    private Color colorCovered =      new Color(150, 255, 150);
    private Color colorANC     =      Color.YELLOW;
    private Color colorNotCovered =    new Color(255, 160, 180);
    private Color colorBorder =     new Color(102, 102, 102);
    
    private RequestProcessor.Task   refreshCoverageTask;

    public CoverageSidebar(JTextComponent target, FileObject file) {
        LOG.log(Level.FINE, "creating DiffSideBar for {0}", file != null ? file.getPath() : null);
        this.textComponent = target;
        this.fileObject = file;
        this.foldHierarchy = FoldHierarchy.get(target);
        this.document = (BaseDocument) textComponent.getDocument();
//        this.markProvider = new DiffMarkProvider();
        setToolTipText(""); // NOI18N
        refreshCoverageTask = WORKER.create(new RefreshCoverageTask());
        setMaximumSize(new Dimension(BAR_WIDTH, Integer.MAX_VALUE));
    }
    
    FileObject getFileObject() {
        return fileObject;
    }

    private void refreshOriginalContent() {
        sidebarTemporarilyDisabled = false;
        LOG.log(Level.FINE, "refreshOriginalContent(): {0}", fileObject != null ? fileObject.getPath() : null);
        refreshCoverage();
    }
    
    JTextComponent getTextComponent() {
        return textComponent;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int line = getLineFromMouseEvent(event);
        if (line == -1) {
            return null;
        }
        LineCoverage c = currentCoverage[line + 1];
        return c != null ? c.comment : null;
    }

    @Override
    public void fileFolderCreated(FileEvent fe) {
        // should not happen
    }

    @Override
    public void fileDataCreated(FileEvent fe) {
        // should not happen
    }

    @Override
    public void fileChanged(FileEvent fe) {
        // not interested
    }

    @Override
    public void fileDeleted(FileEvent fe) {
        if (fileObject != null) {
            // needed since we are changing the fileObject instance
            fileObject.removeFileChangeListener(this);
            fileObject = null;
        }
        DataObject dobj = (DataObject) document.getProperty(Document.StreamDescriptionProperty);
        if (dobj != null) {
            fileObject = dobj.getPrimaryFile();
        }
        fileRenamed(null);
    }

    @Override
    public void fileRenamed(FileRenameEvent fe) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
    }

    @Override
    public void fileAttributeChanged(FileAttributeEvent fe) {
        // not interested
    }

    private int getPosFromY(JTextComponent component, TextUI textUI, int y) throws BadLocationException {
        if(textUI instanceof BaseTextUI) {
            return ((BaseTextUI) textUI).getPosFromY(y);
        } else {
            // fallback to ( less otimized than ((BaseTextUI) textUI).getPosFromY(y) )
            return textUI.modelToView(component, textUI.viewToModel(component, new Point(0, y))).y;
        }
    }

    private int getLineFromMouseEvent(MouseEvent e){
        int line = -1;
        EditorUI editorUI = Utilities.getEditorUI(textComponent);
        if (editorUI != null) {
            try{
                JTextComponent component = editorUI.getComponent();
                if (component != null) {
                    TextUI textUI = component.getUI();
                    int clickOffset = textUI.viewToModel(component, new Point(0, e.getY()));
                    line = Utilities.getLineOffset(document, clickOffset);
                }
            }catch (BadLocationException ble){
                LOG.log(Level.WARNING, "getLineFromMouseEvent", ble); // NOI18N
            }
        }
        return line;
    }

    void refresh() {
        if (!sidebarInComponentHierarchy) {
            return;
        }
        shutdown();
        initialize();
        LOG.finer("refreshing diff in refresh");
        refreshCoverage();
        revalidate();  // resize the component
    }
        
    public void setSidebarVisible(boolean visible) {
        if (sidebarVisible == visible) {
            return;
        }
        sidebarVisible = visible;
        LOG.finer("refreshing diff in setSidebarVisible");
        refreshCoverage();
        revalidate();  // resize the component
    }

    @Override
    public void addNotify() {
        super.addNotify();
        sidebarInComponentHierarchy = true;
        initialize();
    }

    @Override
    public void removeNotify() {
        shutdown();
        sidebarInComponentHierarchy = false;
        super.removeNotify();
    }
    
    private void initialize() {
        assert SwingUtilities.isEventDispatchThread();

        document.addDocumentListener(this);
        textComponent.addComponentListener(this);
        foldHierarchy.addFoldHierarchyListener(this);
        refreshOriginalContent();
        final FileObject fo = fileObject;
        if (fo != null) {
            WORKER.post(new Runnable() {
                @Override
                public void run () {
                    fo.addFileChangeListener(CoverageSidebar.this);
                }
            }).schedule(0);
        }
    }

    private void shutdown() {
        assert SwingUtilities.isEventDispatchThread();
        refreshCoverageTask.cancel();
        final FileObject fo = fileObject;
        if (fo != null) {
            WORKER.post(new Runnable() {
                @Override
                public void run () {
                    fo.removeFileChangeListener(CoverageSidebar.this);
                }
            }).schedule(0);
        }
        foldHierarchy.removeFoldHierarchyListener(this);
        textComponent.removeComponentListener(this);
        document.removeDocumentListener(this);
    }

    private void refreshCoverage() {
        refreshCoverageTask.schedule(50);
    }
        
    static void copyStreamsCloseAll(OutputStream writer, InputStream reader) throws IOException {
        byte [] buffer = new byte[2048];
        int n;
        while ((n = reader.read(buffer)) != -1) {
            writer.write(buffer, 0, n);
        }
        writer.close();
        reader.close();
    }
    
    @Override
    public Dimension getPreferredSize() {
        Dimension dim = textComponent.getSize();
        dim.width = sidebarVisible ? BAR_WIDTH : 0;
        return dim;
    }
    
    @Override
    public void paintComponent(final Graphics g) {
        super.paintComponent(g);
        Utilities.runViewHierarchyTransaction(textComponent, true,
            new Runnable() {
                @Override
                public void run() {
                    paintComponentUnderLock(g);
                }
            }
        );
    }

    private void paintComponentUnderLock (Graphics g) {
        Rectangle clip = g.getClipBounds();
        if (clip.y >= 16) {
            // compensate for scrolling: marks on bottom/top edges are not drawn completely while scrolling
            clip.y -= 16;
            clip.height += 16;
        }

        g.setColor(backgroundColor());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);

        JTextComponent component = textComponent;
        TextUI textUI = component.getUI();
        EditorUI editorUI = Utilities.getEditorUI(textComponent);
        if (editorUI == null) {
            LOG.log(Level.WARNING, "No editor UI for file {0}, has {1} text UI", new Object[] { //NOI18N
                fileObject == null ? null : fileObject.getPath(),
                textComponent.getUI() });
            return;
        }
        View rootView = Utilities.getDocumentView(component);
        if (rootView == null) {
            return;
        }
        
        LineCoverage[] paintCoverage = currentCoverage;
        if (paintCoverage == null || paintCoverage.length == 0) {
            return;
        }

        try{
            int startPos = getPosFromY(component, textUI, clip.y);
            int startViewIndex = rootView.getViewIndex(startPos,Position.Bias.Forward);
            int rootViewCount = rootView.getViewCount();

            if (startViewIndex >= 0 && startViewIndex < rootViewCount) {
                // find the nearest visible line with an annotation
                Rectangle rec = textUI.modelToView(component, rootView.getView(startViewIndex).getStartOffset());
                int y = (rec == null) ? 0 : rec.y;

                int clipEndY = clip.y + clip.height;
                Element rootElem = textUI.getRootView(component).getElement();

                View view = rootView.getView(startViewIndex);
                int line = rootElem.getElementIndex(view.getStartOffset());
                line++; // make it 1-based

                int currentGroup = -1;
                int closeY = -1;

                for (int i = startViewIndex; i < rootViewCount; i++){
                    view = rootView.getView(i);
                    if (view == null) {
                        LOG.log(Level.WARNING, "View {0} null? View count = {1}/{2}. Root view: {3}, root elem: {4}", new Object[] { i, rootView.getViewCount(), rootViewCount, rootView, rootElem });
                    }
                    line = rootElem.getElementIndex(view.getStartOffset());
                    line++; // make it 1-based
                    LineCoverage lc = paintCoverage[line];
                    Rectangle rec1 = component.modelToView(view.getStartOffset());
                    Rectangle rec2 = component.modelToView(view.getEndOffset() - 1);
                    if (rec2  == null || rec1 == null) break;
                    y = rec1.y;
                    double height = (rec2.getY() + rec2.getHeight() - rec1.getY());
                    if (lc != null) {
                        g.setColor(getColor(lc));
                        g.fillRect(3, (int) y, BAR_WIDTH - 3, (int) height);
                        g.setColor(colorBorder);
                        int y1 = closeY = (int) (y + height);
                        g.drawLine(2, (int) y, 2, y1);
                        if (lc.groupId != currentGroup) {
                            g.drawLine(2, closeY, BAR_WIDTH - 1, closeY);
                            g.drawLine(2, (int) y, BAR_WIDTH - 1, (int) y);
                            currentGroup = lc.groupId;
                        }
                    }
                    y += height;
                    if (y >= clipEndY) {
                        break;
                    }
                }
                if (closeY != (-1)) {
                    g.drawLine(2, closeY, BAR_WIDTH - 1, closeY);
                }
            }
        } catch (BadLocationException ble){
            LOG.log(Level.INFO, null, ble);
        }
    }

    private Color getColor(LineCoverage lineCoverage) {
        Color c;

        if (lineCoverage.covered) {
            c = colorCovered;
        } else if (lineCoverage.isANC) {
            c = colorANC;
        } else {
            c = colorNotCovered;
        }

        if (currentFileTimestamp > currentRawCoverageTimestamp) {
            c = c.brighter();
        }

        return c;
    }

    private Color backgroundColor() {
        Container c = getParent();
        if (c == null) {
            return defaultBackground();
        } else {
            return c.getBackground();
        }
    }

    private Color defaultBackground () {
        if (textComponent != null) {
            return textComponent.getBackground();
        }
        return Color.WHITE;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        LOG.finer("refreshing diff in insertUpdate");
        refreshCoverage();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        LOG.finer("refreshing diff in removeUpdate");
        refreshCoverage();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        LOG.finer("refreshing diff in changedUpdate");
        refreshCoverage();
    }

    @Override
    public void componentResized(ComponentEvent e) {
        Mutex.EVENT.readAccess(new Runnable () {
            @Override
            public void run() {
                revalidate();
            }
        });
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    @Override
    public void foldHierarchyChanged(FoldHierarchyEvent evt) {
        Mutex.EVENT.readAccess(new Runnable () {
            @Override
            public void run() {
                repaint();
            }
        });
    }

    /**
     * RP task to compute new diff after a change in the document or a change in the base text.
     */
    public class RefreshCoverageTask implements Runnable {

        @Override
        public void run() {
            computeCoverage();
            checkTimeStamp();
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    repaint();
                }
            });
        }

        private void checkTimeStamp() {
            if (fileObject == null)
                return ;

            EditorCookie ec = fileObject.getLookup().lookup(EditorCookie.class);

            currentFileTimestamp = ec.isModified() ? System.currentTimeMillis() : fileObject.lastModified().getTime();
        }

        private void computeCoverage() {
            if (!sidebarVisible || sidebarTemporarilyDisabled || !sidebarInComponentHierarchy || (currentRawCoverage != null && currentRawCoverage.length == 0)) {
                currentCoverage = null;
                unregisterJCovListener();
                return;
            }
            try {
                ClassPath source = ClassPath.getClassPath(fileObject, ClassPath.SOURCE);
                String fqn = source != null ? source.getResourceName(fileObject, '.', false) : null;

                if (fqn == null || fqn.lastIndexOf('.') == (-1)) {
                    currentCoverage = null;
                    return ;
                }

                File jcovData = new File(new File(new File(org.netbeans.modules.jdk.jtreg.Utilities.jtregOutputDir(fileObject), "jcov"), "jcov.xml"), fqn.replace(".", "/") + ".xml");

                if (!Objects.equals(jcovFile, jcovData)) {
                    unregisterJCovListener();
                    jcovFile = jcovData;
                    FileUtil.addFileChangeListener(jcovFileListener, jcovFile);
                }
                
                if (!jcovData.canRead()) {
                    currentCoverage = null;
                    return ;
                }

                long lastModified = jcovData.lastModified();

                if (currentRawCoverage == null || currentRawCoverageTimestamp != lastModified) {
                    String pack = fqn.substring(0, fqn.lastIndexOf('.'));
                    String simpleFileName = fileObject.getNameExt();

                    LineCoverage[] data = new LineCoverage[10 * 1024]; //XXX
                    ProductCoverage coverage = new ProductCoverage(jcovData.getAbsolutePath(), new AncFilter[] {
                        new CatchANCFilter(),
                        new DeprecatedANCFilter(),
                        new EmptyANCFilter(),
                        new SyntheticANCFilter(),
                        new ThrowANCFilter(),
                    });

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

                                    LineCoverage lc = LineCoverage.from(currentEntry.getKey(), currentEntry.getValue());

                                    for (int l = currentEntry.getKey(); l <= endLine; l++) {
                                        if (!ccov.isCode(l))
                                            continue;
                                        data[l] = lc;
                                    }
                                    currentEntry = nextEntry;
                                } while (currentEntry != null);
                            }
                        }
                    }
                    currentRawCoverage = data;
                    currentRawCoverageTimestamp = lastModified;
                }
            } catch (FileFormatException ex) {
                currentRawCoverage = new LineCoverage[0];
                currentCoverage = null;
                return ;
            }

            LineCookie lc = fileObject.getLookup().lookup(LineCookie.class);

            currentCoverage = new LineCoverage[lc.getLineSet().getLines().size() + 1];

            for (Line l : lc.getLineSet().getLines()) {
                int original = lc.getLineSet().getOriginalLineNumber(l);

                if (original >= currentRawCoverage.length)
                    continue;

                currentCoverage[l.getLineNumber()] = currentRawCoverage[original];
            }
        }

        private void unregisterJCovListener() {
            if (jcovFile != null) {
                FileUtil.removeFileChangeListener(jcovFileListener, jcovFile);
                jcovFile = null;
            }
        }
    }

    private static final class LineCoverage {
        public final int groupId;
        public final boolean covered;
        public final boolean isANC;
        public final String comment;

        private LineCoverage(int groupId, boolean covered, boolean isANC, String comment) {
            this.groupId = groupId;
            this.covered = covered;
            this.isANC = isANC;
            this.comment = comment;
        }

        public static LineCoverage from(int groupId, Iterable<ItemCoverage> items) {
            boolean covered = true;
            boolean anc = true;
            StringBuilder comment = new StringBuilder();

            for (DataType type : new DataType[] {DataType.BLOCK, DataType.BRANCH}) {
                CoverageData data = getCummulativeCoverage(type, items);

                if (data.getTotal() == 0)
                    continue;

                covered &= data.getCovered() == data.getTotal();
                anc &= (data.getCovered() + data.getAnc()) == data.getTotal();

                comment.append(type.getTitle() + ": " + data.getCovered() + (data.getAnc() > 0 ? "(+" + data.getAnc() + ")" : "") + "/" + data.getTotal() + " ");
            }

            return new LineCoverage(groupId, covered, anc, comment.toString());
        }

        private static CoverageData getCummulativeCoverage(DataType type, Iterable<ItemCoverage> items) {
            int coveredBranch = 0;
            int ancBranch = 0;
            int totalBranch = 0;

            for (ItemCoverage ic : items) {
                if (ic.getDataType() == type) {
                    CoverageData data = ic.getData(type);

                    if (data.getTotal() > 0) {
                        coveredBranch += data.getCovered();
                        ancBranch += data.getAnc();
                        totalBranch += data.getTotal();
                    }
                }
            }

            return new CoverageData(coveredBranch, ancBranch, totalBranch);
        }

    }

    public static final class FactoryImpl implements org.netbeans.spi.editor.SideBarFactory {

        @Override
        public JComponent createSideBar(JTextComponent target) {
            return new CoverageSidebar(target, NbEditorUtilities.getFileObject(target.getDocument()));
        }

    }
}
