/*******************************************************************************
 * Copyright (c) 2010 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.scout.rt.client.ui.basic.filechooser;

import java.util.Collection;
import java.util.EventListener;
import java.util.List;

import org.eclipse.scout.commons.Assertions;
import org.eclipse.scout.commons.CollectionUtility;
import org.eclipse.scout.commons.EventListenerList;
import org.eclipse.scout.commons.exception.ProcessingException;
import org.eclipse.scout.commons.resource.BinaryResource;
import org.eclipse.scout.rt.client.context.ClientRunContext;
import org.eclipse.scout.rt.client.context.ClientRunContexts;
import org.eclipse.scout.rt.client.session.ClientSessionProvider;
import org.eclipse.scout.rt.client.ui.desktop.IDesktop;
import org.eclipse.scout.rt.client.ui.desktop.outline.IFileChooserParent;
import org.eclipse.scout.rt.platform.job.IBlockingCondition;
import org.eclipse.scout.rt.platform.job.Jobs;

public class FileChooser implements IFileChooser {

  private final IFileChooserUIFacade m_uiFacade;
  private final EventListenerList m_listenerList = new EventListenerList();

  private List<String> m_fileExtensions;
  private boolean m_multiSelect;
  private List<BinaryResource> m_files;
  private final IBlockingCondition m_blockingCondition;

  private IFileChooserParent m_fileChooserParent;

  public FileChooser() {
    this(null, false);
  }

  public FileChooser(boolean multiSelect) {
    this(null, multiSelect);
  }

  public FileChooser(Collection<String> fileExtensions) {
    this(fileExtensions, false);
  }

  public FileChooser(Collection<String> fileExtensions, boolean multiSelect) {
    m_uiFacade = new P_UIFacade();
    m_blockingCondition = Jobs.getJobManager().createBlockingCondition("block", false);
    m_fileExtensions = CollectionUtility.arrayListWithoutNullElements(fileExtensions);
    m_multiSelect = multiSelect;

    m_fileChooserParent = deriveFileChooserParent();
  }

  @Override
  public IFileChooserUIFacade getUIFacade() {
    return m_uiFacade;
  }

  @Override
  public IFileChooserParent getFileChooserParent() {
    return m_fileChooserParent;
  }

  @Override
  public void setFileChooserParent(IFileChooserParent fileChooserParent) {
    Assertions.assertNotNull(fileChooserParent, "Property 'fileChooserParent' must not be null");
    Assertions.assertFalse(ClientSessionProvider.currentSession().getDesktop().isShowing(this), "Property 'fileChooserParent' cannot be changed because FileChooser is already attached to Desktop [fileChooser=%s]", this);
    m_fileChooserParent = fileChooserParent;
  }

  @Override
  public void addFileChooserListener(FileChooserListener listener) {
    m_listenerList.add(FileChooserListener.class, listener);
  }

  @Override
  public void removeFileChooserListener(FileChooserListener listener) {
    m_listenerList.remove(FileChooserListener.class, listener);
  }

  @Override
  public List<String> getFileExtensions() {
    return CollectionUtility.arrayList(m_fileExtensions);
  }

  @Override
  public boolean isMultiSelect() {
    return m_multiSelect;
  }

  @Override
  public List<BinaryResource> startChooser() throws ProcessingException {
    m_files = null;
    m_blockingCondition.setBlocking(true);

    IDesktop desktop = ClientSessionProvider.currentSession().getDesktop();
    desktop.showFileChooser(this);
    try {
      waitFor();
    }
    finally {
      desktop.hideFileChooser(this);
      fireClosed();
    }
    return getFiles();
  }

  private void waitFor() throws ProcessingException {
    try {
      m_blockingCondition.waitFor();
    }
    finally {
      fireClosed();
    }
  }

  @Override
  public void setFiles(List<BinaryResource> result) {
    m_files = CollectionUtility.arrayListWithoutNullElements(result);
    m_blockingCondition.setBlocking(false);
  }

  @Override
  public List<BinaryResource> getFiles() {
    return CollectionUtility.arrayList(m_files);
  }

  protected void fireClosed() {
    fireFileChooserEvent(new FileChooserEvent(this, FileChooserEvent.TYPE_CLOSED));
  }

  protected void fireFileChooserEvent(FileChooserEvent e) {
    EventListener[] listeners = m_listenerList.getListeners(FileChooserListener.class);
    if (listeners != null && listeners.length > 0) {
      for (int i = 0; i < listeners.length; i++) {
        ((FileChooserListener) listeners[i]).fileChooserChanged(e);
      }
    }
  }

  /**
   * Derives the {@link IFileChooserParent} from the calling context.
   */
  protected IFileChooserParent deriveFileChooserParent() {
    ClientRunContext currentRunContext = ClientRunContexts.copyCurrent();

    // Check whether a Form is currently the FileChooserParent.
    if (currentRunContext.form() != null) {
      return currentRunContext.form();
    }

    // Check whether an Outline is currently the FileChooserParent.
    if (currentRunContext.outline() != null) {
      return currentRunContext.outline();
    }

    // Use the desktop as FileChooserParent.
    return currentRunContext.session().getDesktop();
  }

  private class P_UIFacade implements IFileChooserUIFacade {

    @Override
    public void setResultFromUI(List<BinaryResource> files) {
      setFiles(files);
    }
  }
}
