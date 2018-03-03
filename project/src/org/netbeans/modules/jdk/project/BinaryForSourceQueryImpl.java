/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2018 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.queries.BinaryForSourceQuery.Result;
import org.netbeans.spi.java.queries.BinaryForSourceQueryImplementation;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.ChangeSupport;

/**
 *
 * @author lahvac
 */
public class BinaryForSourceQueryImpl implements BinaryForSourceQueryImplementation {

    private static final Logger LOG = Logger.getLogger(BinaryForSourceQueryImpl.class.getName());

    private final Result result;
    private final ClassPath sourceCP;

    public BinaryForSourceQueryImpl(JDKProject project, ClassPath sourceCP) {
        this.result = new ResultImpl("${outputRoot}/jdk/modules/${module}", project.evaluator());
        this.sourceCP = sourceCP;
    }

    @Override
    public Result findBinaryRoots(URL sourceRoot) {
        FileObject r = URLMapper.findFileObject(sourceRoot);
        if (Arrays.asList(sourceCP.getRoots()).contains(r)) //TODO: faster
            return result;
        return null;
    }

    private static final class ResultImpl implements Result, PropertyChangeListener {

        private final ChangeSupport cs = new ChangeSupport(this);
        private final String template;
        private final PropertyEvaluator evaluator;

        public ResultImpl(String template, PropertyEvaluator evaluator) {
            this.template = template;
            this.evaluator = evaluator;
            this.evaluator.addPropertyChangeListener(this);
        }

        @Override
        public void addChangeListener(ChangeListener l) {
            cs.addChangeListener(l);
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
            cs.removeChangeListener(l);
        }

        @Override
        public URL[] getRoots() {
            try {
                return new URL[] {
                    new URL(evaluator.evaluate(template))
                };
            } catch (MalformedURLException ex) {
                LOG.log(Level.FINE, null, ex);
                return new URL[0];
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent arg0) {
            cs.fireChange();
        }

    }

}
