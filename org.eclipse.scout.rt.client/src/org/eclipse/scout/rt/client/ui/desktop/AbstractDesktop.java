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
package org.eclipse.scout.rt.client.ui.desktop;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.scout.commons.CollectionUtility;
import org.eclipse.scout.commons.ConfigurationUtility;
import org.eclipse.scout.commons.EventListenerList;
import org.eclipse.scout.commons.annotations.ConfigOperation;
import org.eclipse.scout.commons.annotations.ConfigProperty;
import org.eclipse.scout.commons.annotations.ConfigPropertyValue;
import org.eclipse.scout.commons.annotations.Order;
import org.eclipse.scout.commons.beans.AbstractPropertyObserver;
import org.eclipse.scout.commons.exception.IProcessingStatus;
import org.eclipse.scout.commons.exception.ProcessingException;
import org.eclipse.scout.commons.exception.ProcessingStatus;
import org.eclipse.scout.commons.exception.VetoException;
import org.eclipse.scout.commons.logger.IScoutLogger;
import org.eclipse.scout.commons.logger.ScoutLogManager;
import org.eclipse.scout.rt.client.ClientSyncJob;
import org.eclipse.scout.rt.client.services.common.bookmark.internal.BookmarkUtility;
import org.eclipse.scout.rt.client.ui.DataChangeListener;
import org.eclipse.scout.rt.client.ui.action.ActionFinder;
import org.eclipse.scout.rt.client.ui.action.IAction;
import org.eclipse.scout.rt.client.ui.action.keystroke.IKeyStroke;
import org.eclipse.scout.rt.client.ui.action.keystroke.KeyStroke;
import org.eclipse.scout.rt.client.ui.action.menu.IMenu;
import org.eclipse.scout.rt.client.ui.action.tool.IToolButton;
import org.eclipse.scout.rt.client.ui.action.view.IViewButton;
import org.eclipse.scout.rt.client.ui.basic.filechooser.IFileChooser;
import org.eclipse.scout.rt.client.ui.basic.table.ITable;
import org.eclipse.scout.rt.client.ui.basic.tree.ITreeNode;
import org.eclipse.scout.rt.client.ui.basic.tree.TreeAdapter;
import org.eclipse.scout.rt.client.ui.basic.tree.TreeEvent;
import org.eclipse.scout.rt.client.ui.desktop.navigation.INavigationHistoryService;
import org.eclipse.scout.rt.client.ui.desktop.outline.IOutline;
import org.eclipse.scout.rt.client.ui.desktop.outline.IOutlineTableForm;
import org.eclipse.scout.rt.client.ui.desktop.outline.pages.IPage;
import org.eclipse.scout.rt.client.ui.desktop.outline.pages.IPageWithTable;
import org.eclipse.scout.rt.client.ui.desktop.outline.pages.ISearchForm;
import org.eclipse.scout.rt.client.ui.form.FormEvent;
import org.eclipse.scout.rt.client.ui.form.FormListener;
import org.eclipse.scout.rt.client.ui.form.IForm;
import org.eclipse.scout.rt.client.ui.form.PrintDevice;
import org.eclipse.scout.rt.client.ui.form.fields.GridData;
import org.eclipse.scout.rt.client.ui.form.fields.IFormField;
import org.eclipse.scout.rt.client.ui.messagebox.IMessageBox;
import org.eclipse.scout.rt.client.ui.messagebox.MessageBoxEvent;
import org.eclipse.scout.rt.client.ui.messagebox.MessageBoxListener;
import org.eclipse.scout.rt.shared.ScoutTexts;
import org.eclipse.scout.rt.shared.services.common.bookmark.Bookmark;
import org.eclipse.scout.rt.shared.services.common.exceptionhandler.IExceptionHandlerService;
import org.eclipse.scout.service.SERVICES;

/**
 * The desktop model (may) consist of
 * <ul>
 * <li>set of available outline
 * <li>active outline
 * <li>active table view
 * <li>active detail form
 * <li>active search form
 * <li>form stack (swing: dialogs on desktop as JInternalFrames; eclipse: editors or views)
 * <li>dialog stack of model and non-modal dialogs (swing: dialogs as JDialog, JFrame; eclipse: dialogs in a new Shell)
 * <li>active message box stack
 * <li>menubar menus
 * <li>toolbar and viewbar actions
 * </ul>
 * The configurator will create a subclass of this desktop that can be used as
 * initial desktop
 */
public abstract class AbstractDesktop extends AbstractPropertyObserver implements IDesktop {

  private static final IScoutLogger LOG = ScoutLogManager.getLogger(AbstractDesktop.class);

  private final EventListenerList m_listenerList;
  private final Map<Object, EventListenerList> m_dataChangeListenerList;
  private final IDesktopUIFacade m_uiFacade;
  private IOutline[] m_availableOutlines;
  private IOutline m_outline;
  private boolean m_outlineChanging = false;
  private P_ActiveOutlineListener m_activeOutlineListener;
  private P_ActivatedFormListener m_activatedFormListener;
  private LinkedList<WeakReference<IForm>> m_lastActiveFormList;
  private ITable m_pageDetailTable;
  private IOutlineTableForm m_outlineTableForm;
  private boolean m_outlineTableFormVisible;
  private IForm m_pageDetailForm;
  private IForm m_pageSearchForm;
  private final ArrayList<IForm> m_viewStack;
  private final ArrayList<IForm> m_dialogStack;
  private final ArrayList<IMessageBox> m_messageBoxStack;
  private IMenu[] m_menus;
  private IAction[] m_actions;
  private boolean m_autoPrefixWildcardForTextSearch;
  private boolean m_desktopInited;
  private boolean m_trayVisible;

  /**
   * do not instantiate a new desktop<br>
   * get it via {@code ClientScoutSession.getSession().getModelManager()}
   */
  public AbstractDesktop() {
    m_listenerList = new EventListenerList();
    m_dataChangeListenerList = new HashMap<Object, EventListenerList>();
    m_viewStack = new ArrayList<IForm>();
    m_dialogStack = new ArrayList<IForm>();
    m_messageBoxStack = new ArrayList<IMessageBox>();
    m_uiFacade = new P_UIFacade();
    m_outlineTableFormVisible = true;
    initConfig();
  }

  /*
   * Configuration
   */
  @ConfigProperty(ConfigProperty.TEXT)
  @Order(10)
  @ConfigPropertyValue("null")
  protected String getConfiguredTitle() {
    return null;
  }

  @ConfigProperty(ConfigProperty.BOOLEAN)
  @Order(15)
  @ConfigPropertyValue("false")
  protected boolean getConfiguredTrayVisible() {
    return false;
  }

  @ConfigProperty(ConfigProperty.OUTLINES)
  @Order(20)
  @ConfigPropertyValue("null")
  protected Class<? extends IOutline>[] getConfiguredOutlines() {
    return null;
  }

  private Class<? extends IMenu>[] getConfiguredMenus() {
    Class[] dca = ConfigurationUtility.getDeclaredPublicClasses(getClass());
    return ConfigurationUtility.sortFilteredClassesByOrderAnnotation(dca, IMenu.class);
  }

  private Class<? extends IAction>[] getConfiguredActions() {
    Class[] dca = ConfigurationUtility.getDeclaredPublicClasses(getClass());
    return ConfigurationUtility.sortFilteredClassesByOrderAnnotation(dca, IAction.class);
  }

  /**
   * Called while desktop is constructed.
   */
  @ConfigOperation
  @Order(10)
  protected void execInit() throws ProcessingException {

  }

  /**
   * Called after desktop was opened and setup in UI.
   */
  @ConfigOperation
  @Order(12)
  protected void execOpened() throws ProcessingException {

  }

  /**
   * Called before the desktop is being closed. May be vetoed using a {@link VetoException}
   */
  @ConfigOperation
  @Order(15)
  protected void execClosing() throws ProcessingException {

  }

  /**
   * Called after a UI is attached. The desktop must not be necessarily be open.
   */
  @ConfigOperation
  @Order(20)
  protected void execGuiAttached() throws ProcessingException {

  }

  /**
   * Called after a UI is detached. The desktop must not be necessarily be open.
   */
  @ConfigOperation
  @Order(25)
  protected void execGuiDetached() throws ProcessingException {

  }

  /**
   * Called whenever a new outline is activated on the desktop.
   */
  @ConfigOperation
  @Order(30)
  protected void execOutlineChanged(IOutline oldOutline, IOutline newOutline) throws ProcessingException {

  }

  /**
   * Called after an other page was selected.
   * 
   * @param oldForm
   *          is the search form of the old (not selected anymore) page or null
   * @param newForm
   *          is the search form of the new (selected) page or null
   */
  @Order(40)
  @ConfigOperation
  protected void execPageSearchFormChanged(IForm oldForm, IForm newForm) throws ProcessingException {
    if (oldForm != null) {
      removeForm(oldForm);
    }
    if (newForm != null) {
      //ticket 89617: make new form height fixed, non-resizable
      GridData gd = newForm.getRootGroupBox().getGridData();
      if (gd.weightY <= 0) {
        gd.weightY = 0;
        newForm.getRootGroupBox().setGridDataInternal(gd);
      }
      addForm(newForm);
    }
  }

  /**
   * Called after an other page was selected.
   * 
   * @param oldForm
   *          is the detail form of the old (not selected anymore) page or null
   * @param newForm
   *          is the detail form of the new (selected) page or null
   */
  @Order(50)
  @ConfigOperation
  protected void execPageDetailFormChanged(IForm oldForm, IForm newForm) throws ProcessingException {
    if (oldForm != null) {
      removeForm(oldForm);
    }
    if (newForm != null) {
      addForm(newForm);
    }
  }

  /**
   * Called after an other page was selected.
   * 
   * @param oldForm
   *          is the table of the old (not selected anymore) table page or null
   * @param newForm
   *          is the table of the new (selected) table page or null
   */
  @Order(60)
  @ConfigOperation
  protected void execPageDetailTableChanged(ITable oldTable, ITable newTable) throws ProcessingException {
    if (m_outlineTableForm != null) {
      m_outlineTableForm.setCurrentTable(newTable);
    }
    setOutlineTableFormVisible(newTable != null);
  }

  /**
   * Called after a page was loaded or reloaded.
   * <p>
   * Default minimizes page search form when data was found.
   * 
   * @param page
   */
  @Order(62)
  @ConfigOperation
  protected void execTablePageLoaded(IPageWithTable<?> tablePage) throws ProcessingException {
    ISearchForm searchForm = tablePage.getSearchFormInternal();
    if (searchForm != null) {
      searchForm.setMinimized(tablePage.getTable().getRowCount() > 0);
    }
  }

  /**
   * Invoked when the tray popup is being built.
   * <p>
   * May use {@link #getMenu(Class)} to find an existing menu in the desktop by class type.
   * <p>
   * The (potential) menus added to the list will be post processed. {@link IMenu#prepareAction()} is called on each and
   * the checked if the menu is visible.
   */
  @Order(70)
  @ConfigOperation
  protected void execAddTrayMenus(List<IMenu> menus) throws ProcessingException {
  }

  protected void initConfig() {
    setTitle(getConfiguredTitle());
    setTrayVisible(getConfiguredTrayVisible());
    Class[] a = getConfiguredOutlines();
    ArrayList<IOutline> outlineList = new ArrayList<IOutline>();
    if (a != null) {
      for (Class element : a) {
        try {
          IOutline o = (IOutline) element.newInstance();
          o.initTree();
          outlineList.add(o);
        }
        catch (Throwable t) {
          LOG.error(null, t);
        }
      }
    }
    m_availableOutlines = outlineList.toArray(new IOutline[0]);
    // key strokes
    propertySupport.setProperty(PROP_KEY_STROKES, new IKeyStroke[0]);
    ArrayList<IKeyStroke> ksList = new ArrayList<IKeyStroke>();
    Class<? extends IKeyStroke>[] ksArray = getConfiguredKeyStrokes();
    for (int i = 0; i < ksArray.length; i++) {
      try {
        IKeyStroke ks = ConfigurationUtility.newInnerInstance(this, ksArray[i]);
        ksList.add(ks);
      }
      catch (Throwable t) {
        LOG.error(null, t);
      }
    }
    addKeyStrokes(ksList.toArray(new IKeyStroke[ksList.size()]));
    // tools
    ArrayList<IAction> actionList = new ArrayList<IAction>();
    Class<? extends IAction>[] ta = getConfiguredActions();
    for (Class<? extends IAction> element : ta) {
      try {
        IAction tool = ConfigurationUtility.newInnerInstance(this, element);
        actionList.add(tool);
      }
      catch (Exception e) {
        LOG.error(null, e);
      }
    }
    m_actions = actionList.toArray(new IAction[actionList.size()]);
    // menus
    ArrayList<IMenu> menuList = new ArrayList<IMenu>();
    Class<? extends IMenu>[] ma = getConfiguredMenus();
    for (Class<? extends IMenu> element : ma) {
      try {
        IMenu menu = ConfigurationUtility.newInnerInstance(this, element);
        menuList.add(menu);
      }
      catch (Exception e) {
        LOG.error(null, e);
      }
    }
    m_menus = menuList.toArray(new IMenu[0]);
    //add keystrokes for menus with defined keystrokes
    ksList = new ArrayList<IKeyStroke>();
    for (IMenu menu : new ActionFinder().findActions(getMenus(), IMenu.class)) {
      if (menu.getKeyStroke() != null) {
        try {
          IKeyStroke ks = new KeyStroke(menu.getKeyStroke(), menu);
          ksList.add(ks);
        }
        catch (Throwable t) {
          LOG.error(null, t);
        }
      }
    }
    addKeyStrokes(ksList.toArray(new IKeyStroke[ksList.size()]));
  }

  /*
   * Runtime
   */

  public void initDesktop() throws ProcessingException {
    if (!m_desktopInited) {
      m_desktopInited = true;
      // internal
      prepareAllMenus();
      // external
      try {
        execInit();
      }
      catch (ProcessingException e) {
        throw e;
      }
      catch (Throwable t) {
        throw new ProcessingException("init desktop", t);
      }
    }
  }

  public boolean isTrayVisible() {
    return m_trayVisible;
  }

  public void setTrayVisible(boolean b) {
    m_trayVisible = b;
  }

  public boolean isShowing(IForm form) {
    for (IForm f : m_viewStack) {
      if (f == form) {
        return true;
      }
    }
    for (IForm f : m_dialogStack) {
      if (f == form) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  public <T extends IForm> T findForm(Class<T> formType) {
    ArrayList<IForm> list = new ArrayList<IForm>();
    list.addAll(m_viewStack);
    list.addAll(m_dialogStack);
    for (IForm f : list) {
      if (formType.isAssignableFrom(f.getClass())) {
        return (T) f;
      }
    }
    return null;
  }

  public <T extends IAction> T findAction(Class<T> actionType) {
    return new ActionFinder().findAction(getActions(), actionType);
  }

  public <T extends IToolButton> T findToolButton(Class<T> toolButtonType) {
    return findAction(toolButtonType);
  }

  public <T extends IViewButton> T findViewButton(Class<T> viewButtonType) {
    return findAction(viewButtonType);
  }

  public IFormField getFocusOwner() {
    return fireFindFocusOwner();
  }

  @SuppressWarnings("unchecked")
  public <T extends IForm> T[] findForms(Class<T> formType) {
    ArrayList<T> resultList = new ArrayList<T>();
    if (formType != null) {
      ArrayList<IForm> list = new ArrayList<IForm>();
      list.addAll(m_viewStack);
      list.addAll(m_dialogStack);
      for (IForm f : list) {
        if (formType.isAssignableFrom(f.getClass())) {
          resultList.add((T) f);
        }
      }
    }
    return resultList.toArray((T[]) Array.newInstance(formType, resultList.size()));
  }

  @SuppressWarnings("unchecked")
  public <T extends IForm> T findLastActiveForm(Class<T> formType) {
    if (m_lastActiveFormList != null && formType != null) {
      for (WeakReference<IForm> formRef : m_lastActiveFormList) {
        if (formRef.get() != null && formType.isAssignableFrom(formRef.get().getClass())) {
          return (T) formRef.get();
        }
      }
    }
    return null;
  }

  public <T extends IMenu> T getMenu(Class<? extends T> searchType) {
    return new ActionFinder().findAction(getMenus(), searchType);
  }

  public IForm[] getViewStack() {
    return m_viewStack.toArray(new IForm[0]);
  }

  public IForm[] getDialogStack() {
    return m_dialogStack.toArray(new IForm[0]);
  }

  /**
   * returns all forms except the searchform and the current detail form with
   * the same fully qualified classname and an equal primary key different from
   * null.
   * 
   * @param form
   * @return
   */
  public IForm[] getSimilarViewForms(IForm form) {
    ArrayList<IForm> forms = new ArrayList<IForm>(3);
    try {
      if (form != null && form.computeExclusiveKey() != null) {
        Object originalKey = form.computeExclusiveKey();
        for (IForm f : m_viewStack) {
          Object candidateKey = f.computeExclusiveKey();
          if (getPageDetailForm() == f || getPageSearchForm() == f) {
            continue;
          }
          else if (candidateKey == null || originalKey == null) {
            continue;
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("form: " + candidateKey + " vs " + originalKey);
            }

            if (f.getClass().getName().equals(form.getClass().getName()) && originalKey.equals(candidateKey)) {
              forms.add(f);
            }
          }
        }
      }
    }
    catch (ProcessingException e) {
      SERVICES.getService(IExceptionHandlerService.class).handleException(e);
    }
    return forms.toArray(new IForm[forms.size()]);
  }

  public void ensureViewStackVisible() {
    IForm[] viewStack = getViewStack();
    for (IForm form : viewStack) {
      ensureVisible(form);
    }
  }

  public void ensureVisible(IForm form) {
    if (form != null) {
      if (m_viewStack.contains(form) || m_dialogStack.contains(form)) {
        fireFormEnsureVisible(form);
      }
    }
  }

  public void addForm(final IForm form) {
    if (form != null) {
      switch (form.getDisplayHint()) {
        case IForm.DISPLAY_HINT_POPUP_WINDOW:
        case IForm.DISPLAY_HINT_POPUP_DIALOG:
        case IForm.DISPLAY_HINT_DIALOG: {
          if (m_viewStack.remove(form)) {
            fireFormRemoved(form);
          }
          //remove all open popup windows
          if (form.getDisplayHint() == IForm.DISPLAY_HINT_POPUP_WINDOW) {
            for (IForm f : new ArrayList<IForm>(m_dialogStack)) {
              if (f.getDisplayHint() == IForm.DISPLAY_HINT_POPUP_WINDOW) {
                try {
                  f.doClose();
                }
                catch (Throwable t) {
                  LOG.error("Failed closing popup " + f, t);
                }
              }
            }
          }
          if (!m_dialogStack.contains(form)) {
            m_dialogStack.add(form);
            fireFormAdded(form);
          }
          break;
        }
        case IForm.DISPLAY_HINT_VIEW: {
          if (m_dialogStack.remove(form)) {
            fireFormRemoved(form);
          }
          if (!m_viewStack.contains(form)) {
            m_viewStack.add(form);
            fireFormAdded(form);
          }
          break;
        }
      }
      if (m_lastActiveFormList == null) {
        m_lastActiveFormList = new LinkedList<WeakReference<IForm>>();
      }
      m_lastActiveFormList.add(new WeakReference<IForm>(form));
      form.addFormListener(m_activatedFormListener);
    }
  }

  public void removeForm(IForm form) {
    if (form != null) {
      form.removeFormListener(m_activatedFormListener);
      boolean b1 = m_dialogStack.remove(form);
      boolean b2 = m_viewStack.remove(form);
      if (b1 || b2) {
        fireFormRemoved(form);
      }
    }
  }

  public IMessageBox[] getMessageBoxStack() {
    return m_messageBoxStack.toArray(new IMessageBox[0]);
  }

  public void addMessageBox(final IMessageBox mb) {
    m_messageBoxStack.add(mb);
    mb.addMessageBoxListener(new MessageBoxListener() {
      public void messageBoxChanged(MessageBoxEvent e) {
        switch (e.getType()) {
          case MessageBoxEvent.TYPE_CLOSED: {
            removeMessageBoxInternal(mb);
          }
        }
      }
    });
    fireMessageBoxAdded(mb);
  }

  private void removeMessageBoxInternal(IMessageBox mb) {
    m_messageBoxStack.remove(mb);
  }

  public IOutline[] getAvailableOutlines() {
    return m_availableOutlines;
  }

  public void setAvailableOutlines(IOutline[] availableOutlines) {
    setOutline((IOutline) null);
    m_availableOutlines = availableOutlines != null ? availableOutlines : new IOutline[0];
  }

  public IOutline getOutline() {
    return m_outline;
  }

  public void setOutline(IOutline outline) {
    outline = resolveOutline(outline);
    if (m_outline == outline
        || m_outlineChanging) {
      return;
    }
    synchronized (this) {
      try {
        m_outlineChanging = true;
        if (m_outline != null) {
          IPage oldActivePage = m_outline.getActivePage();
          if (oldActivePage != null) {
            SERVICES.getService(INavigationHistoryService.class).addStep(0, oldActivePage.getCell().getText(), oldActivePage.getCell().getIconId());
          }
        }
        //
        IOutline oldOutline = m_outline;
        if (m_activeOutlineListener != null && oldOutline != null) {
          oldOutline.removeTreeListener(m_activeOutlineListener);
          oldOutline.removePropertyChangeListener(m_activeOutlineListener);
          m_activeOutlineListener = null;
        }
        // set new outline to set facts
        m_outline = outline;
        // deactivate old page
        if (oldOutline != null) {
          oldOutline.clearContextPage();
        }
        //
        if (m_outline != null) {
          m_activeOutlineListener = new P_ActiveOutlineListener();
          m_outline.addTreeListener(m_activeOutlineListener);
          m_outline.addPropertyChangeListener(m_activeOutlineListener);
        }
        // <bsh 2010-10-15>
        // Those three "setXyz(null)" statements used to be called unconditionally. Now, they
        // are only called when the new outline is null. When the new outline is _not_ null, we
        // will override the "null" anyway (see below).
        // This change is needed for the "on/off semantics" of the tool tab buttons to work correctly.
        if (m_outline == null) {
          setPageDetailForm(null);
          setPageDetailTable(null);
          setPageSearchForm(null, true);
        }
        // </bsh>
        fireOutlineChanged(oldOutline, m_outline);
        if (m_outline != null) {
          // reload selected page in case it is marked dirty
          if (m_outline.getActivePage() != null) {
            try {
              m_outline.getActivePage().ensureChildrenLoaded();
            }
            catch (ProcessingException e) {
              SERVICES.getService(IExceptionHandlerService.class).handleException(e);
            }
          }
          m_outline.setNodeExpanded(m_outline.getRootNode(), true);
          setPageDetailForm(m_outline.getDetailForm());
          setPageDetailTable(m_outline.getDetailTable());
          setPageSearchForm(m_outline.getSearchForm(), true);
          m_outline.makeActivePageToContextPage();
          IPage newActivePage = m_outline.getActivePage();
          if (newActivePage == null) {
            // if there is no active page, set it now
            if (m_outline.isRootNodeVisible()) {
              m_outline.selectNode(m_outline.getRootNode(), false);
            }
            else {
              ITreeNode[] children = m_outline.getRootNode().getChildNodes();
              if (children.length > 0) {
                m_outline.selectNode(children[0], false);
              }
            }
            newActivePage = m_outline.getActivePage();
          }
          if (newActivePage != null) {
            SERVICES.getService(INavigationHistoryService.class).addStep(0, newActivePage.getCell().getText(), newActivePage.getCell().getIconId());
          }
        }
      }
      finally {
        m_outlineChanging = false;
      }
    }
  }

  private IOutline resolveOutline(IOutline outline) {
    for (IOutline o : getAvailableOutlines()) {
      if (o == outline) {
        return o;
      }
    }
    return null;
  }

  public void setOutline(Class<? extends IOutline> outlineType) {
    for (IOutline o : getAvailableOutlines()) {
      if (o.getClass() == outlineType) {
        setOutline(o);
        return;
      }
    }
  }

  public IKeyStroke[] getKeyStrokes() {
    return (IKeyStroke[]) propertySupport.getProperty(PROP_KEY_STROKES);
  }

  public void setKeyStrokes(IKeyStroke[] ks) {
    if (ks == null) ks = new IKeyStroke[0];
    propertySupport.setProperty(PROP_KEY_STROKES, ks);
  }

  public void addKeyStrokes(IKeyStroke... keyStrokes) {
    if (keyStrokes != null && keyStrokes.length > 0) {
      HashMap<String, IKeyStroke> map = new HashMap<String, IKeyStroke>();
      for (IKeyStroke ks : getKeyStrokes()) {
        map.put(ks.getKeyStroke(), ks);
      }
      for (IKeyStroke ks : keyStrokes) {
        map.put(ks.getKeyStroke(), ks);
      }
      setKeyStrokes(map.values().toArray(new IKeyStroke[map.size()]));
    }
  }

  public void removeKeyStrokes(IKeyStroke... keyStrokes) {
    if (keyStrokes != null && keyStrokes.length > 0) {
      HashMap<String, IKeyStroke> map = new HashMap<String, IKeyStroke>();
      for (IKeyStroke ks : getKeyStrokes()) {
        map.put(ks.getKeyStroke(), ks);
      }
      for (IKeyStroke ks : keyStrokes) {
        map.remove(ks.getKeyStroke());
      }
      setKeyStrokes(map.values().toArray(new IKeyStroke[map.size()]));
    }
  }

  private Class<? extends IKeyStroke>[] getConfiguredKeyStrokes() {
    Class[] dca = ConfigurationUtility.getDeclaredPublicClasses(getClass());
    return ConfigurationUtility.filterClasses(dca, IKeyStroke.class);
  }

  public IMenu[] getMenus() {
    return m_menus;
  }

  public void prepareAllMenus() {
    for (IMenu child : getMenus()) {
      prepareMenuRec(child);
    }
  }

  private void prepareMenuRec(IMenu menu) {
    menu.prepareAction();
    for (IMenu child : menu.getChildActions()) {
      prepareMenuRec(child);
    }
  }

  public IAction[] getActions() {
    return m_actions;
  }

  public IToolButton[] getToolButtons() {
    ArrayList<IToolButton> list = new ArrayList<IToolButton>(m_actions.length);
    for (IAction a : getActions()) {
      if (a instanceof IToolButton) {
        list.add((IToolButton) a);
      }
    }
    return list.toArray(new IToolButton[list.size()]);
  }

  public IViewButton[] getViewButtons() {
    ArrayList<IViewButton> list = new ArrayList<IViewButton>(m_actions.length);
    for (IAction a : getActions()) {
      if (a instanceof IViewButton) {
        list.add((IViewButton) a);
      }
    }
    return list.toArray(new IViewButton[list.size()]);
  }

  public IForm getPageDetailForm() {
    return m_pageDetailForm;
  }

  public void setPageDetailForm(IForm f) {
    if (m_pageDetailForm != f) {
      IForm oldForm = m_pageDetailForm;
      m_pageDetailForm = f;
      try {
        execPageDetailFormChanged(oldForm, m_pageDetailForm);
      }
      catch (Throwable t) {
        LOG.error(null, t);
      }
    }
  }

  public IForm getPageSearchForm() {
    return m_pageSearchForm;
  }

  public void setPageSearchForm(IForm f) {
    setPageSearchForm(f, false);
  }

  public void setPageSearchForm(IForm f, boolean force) {
    if (force || m_pageSearchForm != f) {
      IForm oldForm = m_pageSearchForm;
      m_pageSearchForm = f;
      try {
        execPageSearchFormChanged(oldForm, m_pageSearchForm);
      }
      catch (Throwable t) {
        LOG.error(null, t);
      }
    }
  }

  public IOutlineTableForm getOutlineTableForm() {
    return m_outlineTableForm;
  }

  public void setOutlineTableForm(IOutlineTableForm f) {
    if (f != m_outlineTableForm) {
      if (m_outlineTableForm != null) {
        removeForm(m_outlineTableForm);
      }
      m_outlineTableForm = f;
      if (m_outlineTableForm != null) {
        m_outlineTableForm.setCurrentTable(getPageDetailTable());
        setOutlineTableFormVisible(getPageDetailTable() != null);
      }
      if (m_outlineTableForm != null && m_outlineTableFormVisible) {
        addForm(m_outlineTableForm);
      }
    }
  }

  public boolean isOutlineTableFormVisible() {
    return m_outlineTableFormVisible;
  }

  public void setOutlineTableFormVisible(boolean b) {
    if (m_outlineTableFormVisible != b) {
      m_outlineTableFormVisible = b;
      if (m_outlineTableForm != null) {
        if (m_outlineTableFormVisible) {
          addForm(m_outlineTableForm);
        }
        else {
          removeForm(m_outlineTableForm);
        }
      }
    }
  }

  public ITable getPageDetailTable() {
    return m_pageDetailTable;
  }

  public void setPageDetailTable(ITable t) {
    if (m_pageDetailTable != t) {
      ITable oldTable = m_pageDetailTable;
      m_pageDetailTable = t;
      try {
        execPageDetailTableChanged(oldTable, m_pageDetailTable);
      }
      catch (Throwable x) {
        LOG.error(null, x);
      }
    }
  }

  public String getTitle() {
    return propertySupport.getPropertyString(PROP_TITLE);
  }

  public void setTitle(String s) {
    propertySupport.setPropertyString(PROP_TITLE, s);
  }

  public IProcessingStatus getStatus() {
    return (IProcessingStatus) propertySupport.getProperty(PROP_STATUS);
  }

  public void setStatus(IProcessingStatus status) {
    propertySupport.setProperty(PROP_STATUS, status);
  }

  public void setStatusText(String s) {
    if (s != null) {
      setStatus(new ProcessingStatus(s, null, 0, IProcessingStatus.INFO));
    }
    else {
      setStatus(null);
    }
  }

  public void printDesktop(PrintDevice device, Map<String, Object> parameters) {
    try {
      firePrint(device, parameters);
    }
    catch (ProcessingException e) {
      e.addContextMessage(ScoutTexts.get("FormPrint") + " " + getTitle());
      SERVICES.getService(IExceptionHandlerService.class).handleException(e);
    }
  }

  public void addFileChooser(IFileChooser fc) {
    fireFileChooserAdded(fc);
  }

  public boolean isAutoPrefixWildcardForTextSearch() {
    return m_autoPrefixWildcardForTextSearch;
  }

  public void setAutoPrefixWildcardForTextSearch(boolean b) {
    m_autoPrefixWildcardForTextSearch = b;
  }

  public boolean isOpened() {
    return propertySupport.getPropertyBool(PROP_OPENED);
  }

  private void setOpenedInternal(boolean b) {
    propertySupport.setPropertyBool(PROP_OPENED, b);
  }

  private void setGuiAvailableInternal(boolean guiAvailable) {
    propertySupport.setPropertyBool(PROP_GUI_AVAILABLE, guiAvailable);
  }

  public boolean isGuiAvailable() {
    return propertySupport.getPropertyBool(PROP_GUI_AVAILABLE);
  }

  public void addDesktopListener(DesktopListener l) {
    m_listenerList.add(DesktopListener.class, l);
  }

  public void removeDesktopListener(DesktopListener l) {
    m_listenerList.remove(DesktopListener.class, l);
  }

  public void addDataChangeListener(DataChangeListener listener, Object... dataTypes) {
    if (dataTypes == null || dataTypes.length == 0) {
      EventListenerList list = m_dataChangeListenerList.get(null);
      if (list == null) {
        list = new EventListenerList();
        m_dataChangeListenerList.put(null, list);
      }
      list.add(DataChangeListener.class, listener);
    }
    else {
      for (Object dataType : dataTypes) {
        if (dataType != null) {
          EventListenerList list = m_dataChangeListenerList.get(dataType);
          if (list == null) {
            list = new EventListenerList();
            m_dataChangeListenerList.put(dataType, list);
          }
          list.add(DataChangeListener.class, listener);
        }
      }
    }
  }

  public void removeDataChangeListener(DataChangeListener listener, Object... dataTypes) {
    if (dataTypes == null || dataTypes.length == 0) {
      for (Iterator<EventListenerList> it = m_dataChangeListenerList.values().iterator(); it.hasNext();) {
        EventListenerList list = it.next();
        list.remove(DataChangeListener.class, listener);
        if (list.getListenerCount(DataChangeListener.class) == 0) {
          it.remove();
        }
      }
    }
    else {
      for (Object dataType : dataTypes) {
        if (dataType != null) {
          EventListenerList list = m_dataChangeListenerList.get(dataType);
          if (list != null) {
            list.remove(DataChangeListener.class, listener);
            if (list.getListenerCount(DataChangeListener.class) == 0) {
              m_dataChangeListenerList.remove(dataType);
            }
          }
        }
      }
    }
  }

  public void dataChanged(Object... dataTypes) {
    if (dataTypes != null && dataTypes.length > 0) {
      HashMap<DataChangeListener, Set<Object>> map = new HashMap<DataChangeListener, Set<Object>>();
      for (Object dataType : dataTypes) {
        if (dataType != null) {
          EventListenerList list = m_dataChangeListenerList.get(dataType);
          if (list != null) {
            for (DataChangeListener listener : list.getListeners(DataChangeListener.class)) {
              Set<Object> typeSet = map.get(listener);
              if (typeSet == null) {
                typeSet = new HashSet<Object>();
                map.put(listener, typeSet);
              }
              typeSet.add(dataType);
            }
          }
        }
      }
      for (Map.Entry<DataChangeListener, Set<Object>> e : map.entrySet()) {
        DataChangeListener listener = e.getKey();
        Set<Object> typeSet = e.getValue();
        try {
          listener.dataChanged(typeSet.toArray());
        }
        catch (Throwable t) {
          LOG.error(null, t);
        }
      }
    }
  }

  private void fireDesktopClosed() {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_DESKTOP_CLOSED);
    fireDesktopEvent(e);
  }

  private void firePrint(PrintDevice device, Map<String, Object> parameters) throws ProcessingException {
    fireDesktopEvent(new DesktopEvent(this, DesktopEvent.TYPE_PRINT, device, parameters));
  }

  private IMenu[] fireTrayPopup() {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_TRAY_POPUP);
    // single observer for exec callback
    addLocalPopupMenus(e);
    fireDesktopEvent(e);
    return e.getPopupMenus();
  }

  private void fireOutlineChanged(IOutline oldOutline, IOutline newOutline) {
    if (oldOutline != newOutline) {
      // single observer callback
      try {
        execOutlineChanged(oldOutline, newOutline);
      }
      catch (ProcessingException t) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(t);
      }
      catch (Throwable t) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(new ProcessingException(oldOutline + " -> " + newOutline, t));
      }
    }
    // fire
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_OUTLINE_CHANGED, newOutline);
    fireDesktopEvent(e);
  }

  private void fireFormAdded(IForm form) {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_FORM_ADDED, form);
    fireDesktopEvent(e);
  }

  private void fireFormEnsureVisible(IForm form) {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_FORM_ENSURE_VISIBLE, form);
    fireDesktopEvent(e);
  }

  private void fireFormRemoved(IForm form) {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_FORM_REMOVED, form);
    fireDesktopEvent(e);
  }

  private void fireMessageBoxAdded(IMessageBox mb) {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_MESSAGE_BOX_ADDED, mb);
    fireDesktopEvent(e);
  }

  private void fireFileChooserAdded(IFileChooser fc) {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_FILE_CHOOSER_ADDED, fc);
    fireDesktopEvent(e);
  }

  private IFormField fireFindFocusOwner() {
    DesktopEvent e = new DesktopEvent(this, DesktopEvent.TYPE_FIND_FOCUS_OWNER);
    fireDesktopEvent(e);
    return e.getFocusedField();
  }

  // main handler
  private void fireDesktopEvent(DesktopEvent e) {
    EventListener[] listeners = m_listenerList.getListeners(DesktopListener.class);
    if (listeners != null && listeners.length > 0) {
      for (EventListener element : listeners) {
        try {
          ((DesktopListener) element).desktopChanged(e);
        }
        catch (Throwable t) {
          LOG.error(null, t);
        }
      }
    }
  }

  private void addLocalPopupMenus(DesktopEvent event) {
    try {
      ArrayList<IMenu> list = new ArrayList<IMenu>();
      execAddTrayMenus(list);
      for (IMenu m : list) {
        if (m != null) m.prepareAction();
      }
      for (IMenu m : list) {
        if (m != null && m.isVisible()) event.addPopupMenu(m);
      }
    }
    catch (ProcessingException e) {
      SERVICES.getService(IExceptionHandlerService.class).handleException(e);
    }
    catch (Throwable t) {
      SERVICES.getService(IExceptionHandlerService.class).handleException(new ProcessingException("Unexpected", t));
    }
  }

  public void activateBookmark(Bookmark bm, boolean forceReload) throws ProcessingException {
    BookmarkUtility.activateBookmark(this, bm, forceReload);
  }

  public Bookmark createBookmark() throws ProcessingException {
    return BookmarkUtility.createBookmark(this);
  }

  public void refreshPages(Class... pageTypes) {
    for (IOutline outline : getAvailableOutlines()) {
      outline.refreshPages(pageTypes);
    }
  }

  public void releaseUnusedPages() {
    for (IOutline outline : getAvailableOutlines()) {
      outline.releaseUnusedPages();
    }
  }

  public void afterTablePageLoaded(IPageWithTable<?> tablePage) throws ProcessingException {
    execTablePageLoaded(tablePage);
  }

  public void closeInternal() throws ProcessingException {
    execClosing();
    fireDesktopClosed();
  }

  public boolean runMenu(Class<? extends IMenu> menuType) throws ProcessingException {
    for (IMenu m : getMenus()) {
      if (runMenuRec(m, menuType)) return true;
    }
    return false;
  }

  private boolean runMenuRec(IMenu m, Class<? extends IMenu> menuType) throws ProcessingException {
    if (m.getClass() == menuType) {
      m.prepareAction();
      if (m.isVisible() && m.isEnabled()) {
        m.doAction();
        return true;
      }
      else {
        return false;
      }
    }
    // children
    for (IMenu c : m.getChildActions()) {
      if (runMenuRec(c, menuType)) return true;
    }
    return false;
  }

  public IDesktopUIFacade getUIFacade() {
    return m_uiFacade;
  }

  /*
   * UI Facade
   */
  private class P_UIFacade implements IDesktopUIFacade {

    public void fireGuiAttached() {
      try {
        setGuiAvailableInternal(true);
        execGuiAttached();
      }
      catch (ProcessingException e) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(e);
      }
      catch (Throwable t) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(new ProcessingException("Unexpected", t));
      }
    }

    public void fireGuiDetached() {
      try {
        setGuiAvailableInternal(false);
        execGuiDetached();
      }
      catch (ProcessingException e) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(e);
      }
      catch (Throwable t) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(new ProcessingException("Unexpected", t));
      }
    }

    public void fireDesktopOpenedFromUI() {
      try {
        setOpenedInternal(true);
        execOpened();
      }
      catch (ProcessingException e) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(e);
      }
      catch (Throwable t) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(new ProcessingException("Unexpected", t));
      }
    }

    public void fireDesktopResetFromUI() {
      try {
        setOpenedInternal(false);
        execClosing();
        execOpened();
        setOpenedInternal(true);
      }
      catch (ProcessingException e) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(e);
      }
      catch (Throwable t) {
        SERVICES.getService(IExceptionHandlerService.class).handleException(new ProcessingException("Unexpected", t));
      }
    }

    public void fireDesktopClosingFromUI() {
      setOpenedInternal(false);
      ClientSyncJob.getCurrentSession().stopSession();
    }

    public IMenu[] fireTrayPopupFromUI() {
      return fireTrayPopup();
    }

  }

  private class P_ActiveOutlineListener extends TreeAdapter implements PropertyChangeListener {
    @Override
    public void treeChanged(TreeEvent e) {
      switch (e.getType()) {
        case TreeEvent.TYPE_BEFORE_NODES_SELECTED: {
          IPage page = m_outline.getActivePage();
          if (page != null) {
            SERVICES.getService(INavigationHistoryService.class).addStep(0, page.getCell().getText(), page.getCell().getIconId());
          }
          break;
        }
        case TreeEvent.TYPE_NODES_SELECTED: {
          IPage page = m_outline.getActivePage();
          if (page != null) {
            SERVICES.getService(INavigationHistoryService.class).addStep(0, page.getCell().getText(), page.getCell().getIconId());
          }
          ClientSyncJob.getCurrentSession().getMemoryPolicy().afterOutlineSelectionChanged(AbstractDesktop.this);
          break;
        }
      }
    }

    public void propertyChange(PropertyChangeEvent e) {
      if (e.getPropertyName().equals(IOutline.PROP_DETAIL_FORM)) {
        setPageDetailForm(((IOutline) e.getSource()).getDetailForm());
      }
      else if (e.getPropertyName().equals(IOutline.PROP_DETAIL_TABLE)) {
        setPageDetailTable(((IOutline) e.getSource()).getDetailTable());
      }
      else if (e.getPropertyName().equals(IOutline.PROP_SEARCH_FORM)) {
        setPageSearchForm(((IOutline) e.getSource()).getSearchForm());
      }
    }
  }

  private class P_ActivatedFormListener implements FormListener {
    public void formChanged(FormEvent e) throws ProcessingException {
      if (m_lastActiveFormList == null) {
        m_lastActiveFormList = new LinkedList<WeakReference<IForm>>();
      }
      List<WeakReference<IForm>> nullReferences = null;
      WeakReference<IForm> oldReference = null;
      for (WeakReference<IForm> formRef : m_lastActiveFormList) {
        if (formRef.get() == null) {
          nullReferences = CollectionUtility.appendList(nullReferences, formRef);
        }
        else if (formRef.get().equals(e.getForm())) {
          oldReference = formRef;
        }
      }
      m_lastActiveFormList.removeAll(CollectionUtility.copyList(nullReferences));
      if (oldReference != null) {
        m_lastActiveFormList.remove(oldReference);
      }

      m_lastActiveFormList.add(new WeakReference<IForm>(e.getForm()));
    }
  }

  public void changeVisibilityAfterOfflineSwitch() {
    return;
  }
}
