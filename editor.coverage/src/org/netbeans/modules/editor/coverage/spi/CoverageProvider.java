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
package org.netbeans.modules.editor.coverage.spi;

import java.util.Arrays;
import java.util.prefs.Preferences;

import javax.swing.event.ChangeListener;

import org.netbeans.modules.editor.coverage.CoverageSidebar;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public interface CoverageProvider {

    public static boolean isCoverageEnabled() {
        Preferences prefs = CoverageSidebar.getPreferences();
        return CoverageSidebar.isSidebarEnabled(prefs);
    }
    public CoverageData getCoverage();
    public void addChangeListener(ChangeListener l);
    public void removeChangeListener(ChangeListener l);

    public interface Factory {
        public CoverageProvider createProvider(FileObject file);
    }

    public static final class CoverageData {
        private final Iterable<? extends Coverage> coverage;
        private final long timeStamp;

        public CoverageData(Iterable<? extends Coverage> coverage, long timeStamp) {
            this.coverage = coverage;
            this.timeStamp = timeStamp;
        }

        public Iterable<? extends Coverage> getCoverage() {
            return coverage;
        }

        public long getTimeStamp() {
            return timeStamp;
        }
        
    }

    public static final class Coverage {
        private final int[] lines;
        public final CoverageType type;
        public final String comment;

        public Coverage(int[] lines, CoverageType type, String comment) {
            this.lines = lines;
            this.type = type;
            this.comment = comment;
        }

        public int[] getLines() {
            return Arrays.copyOf(lines, lines.length);
        }

    }

    public enum CoverageType {
        COVERED,
        NOT_COVERED_ACCEPTABLE,
        NOT_COVERED;
    }
}
