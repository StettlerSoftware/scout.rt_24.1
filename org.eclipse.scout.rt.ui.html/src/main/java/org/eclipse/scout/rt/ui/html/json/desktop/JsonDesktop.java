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
package org.eclipse.scout.rt.ui.html.json.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.scout.rt.client.ui.action.IAction;
import org.eclipse.scout.rt.client.ui.action.keystroke.IKeyStroke;
import org.eclipse.scout.rt.client.ui.action.view.IViewButton;
import org.eclipse.scout.rt.client.ui.desktop.DesktopEvent;
import org.eclipse.scout.rt.client.ui.desktop.DesktopListener;
import org.eclipse.scout.rt.client.ui.desktop.IDesktop;
import org.eclipse.scout.rt.client.ui.desktop.IDownloadHandler;
import org.eclipse.scout.rt.client.ui.desktop.ITargetWindow;
import org.eclipse.scout.rt.client.ui.desktop.TargetWindow;
import org.eclipse.scout.rt.client.ui.desktop.outline.IOutline;
import org.eclipse.scout.rt.client.ui.desktop.outline.IOutlineTableForm;
import org.eclipse.scout.rt.client.ui.desktop.outline.IOutlineTreeForm;
import org.eclipse.scout.rt.client.ui.desktop.outline.ISearchOutline;
import org.eclipse.scout.rt.client.ui.form.IForm;
import org.eclipse.scout.rt.client.ui.messagebox.IMessageBox;
import org.eclipse.scout.rt.shared.data.basic.BinaryResource;
import org.eclipse.scout.rt.ui.html.IUiSession;
import org.eclipse.scout.rt.ui.html.json.AbstractJsonPropertyObserver;
import org.eclipse.scout.rt.ui.html.json.IJsonAdapter;
import org.eclipse.scout.rt.ui.html.json.JsonEvent;
import org.eclipse.scout.rt.ui.html.json.JsonObjectUtility;
import org.eclipse.scout.rt.ui.html.json.JsonProperty;
import org.eclipse.scout.rt.ui.html.json.action.DisplayableActionFilter;
import org.eclipse.scout.rt.ui.html.res.BinaryResourceUrlUtility;
import org.eclipse.scout.rt.ui.html.res.IBinaryResourceProvider;
import org.json.JSONObject;

public class JsonDesktop<T extends IDesktop> extends AbstractJsonPropertyObserver<T> implements IBinaryResourceProvider {

  public static final String PROP_OUTLINE = "outline";
  public static final String PROP_FORM = "form";
  public static final String PROP_MESSAGE_BOX = "messageBox";

  private Map<String, IDownloadHandler> m_downloadHandlers = new HashMap<String, IDownloadHandler>();
  private DesktopListener m_desktopListener;

  public JsonDesktop(T model, IUiSession uiSession, String id, IJsonAdapter<?> parent) {
    super(model, uiSession, id, parent);
  }

  @Override
  public String getObjectType() {
    return "Desktop";
  }

  @Override
  protected void attachChildAdapters() {
    super.attachChildAdapters();
    attachGlobalAdapters(getViews());
    attachGlobalAdapters(getDialogs());
    attachGlobalAdapters(getModel().getMessageBoxStack());
    attachAdapters(filterModelActions(), new DisplayableActionFilter<IAction>());
    attachAdapters(getModel().getAddOns());
    attachAdapters(getModel().getKeyStrokes(), new DisplayableActionFilter<IKeyStroke>());
    if (!isFormBased()) {
      attachAdapters(getModel().getViewButtons(), new DisplayableActionFilter<IViewButton>());
      attachGlobalAdapter(getModel().getOutline(), new DisplayableOutlineFilter<IOutline>());
      attachGlobalAdapter(getSearchOutline(), new DisplayableOutlineFilter<IOutline>());
    }
  }

  /**
   * TODO AWE/CGU: (scout, actions) mit Judith besprechen: Aufräumaktion im Bereich IToolButton/IViewButton
   * Anstelle der vielen Marker interfaces möchten wir nur noch IActions haben. Alle IActions werden dann
   * rechts oben angezeigt (es können Menüs und Buttons sein). Die Outlines werden einfach anhand der
   * konfigurierten Outlines erzeugt, ohne dass dafür noch ein Button konfiguriert werden muss. Dann
   * können alle Interfaces und abstrakten Klasse die es heute gibt gelöscht werden.
   * <p>
   * An dieser Stelle filtern wir einfach alle Actions weg, die wir im MiniCRM nicht sehen wollen. Wenn wir den Fork
   * haben, können wir die Konfiguration anpassen. Nach dem Refactoring diese Methode entfernen und nur noch
   * model.getActions() verwenden.
   * <p>
   * Mit Scout-Team besprechen, wie wir mit Menüs in Zukunft umgehen wollen. Wollen wir die Menü-Bar im Model abbilden?
   * (GroupBox, Table, Desktop) Brauchen wir all die Menü-Types dann noch? Können wir die Menü-Logik überhaupt nochmals
   * refactoren? Was machen wir mit der heutigen Button-Bar?
   */
  protected List<IAction> filterModelActions() {
    List<IAction> result = new ArrayList<>();
    for (IAction a : getModel().getActions()) {
      //FIXME CGU remove demo as soon as demo tool forms are correct
      if (!(a instanceof IKeyStroke) && !(a instanceof IViewButton) && !a.getClass().getName().startsWith("org.eclipsescout.demo")) {
        result.add(a);
      }
    }
    // Noch ein Hack: in AbstractDesktop#getActions() ist hart-kodiert, dass menus vor toolButtons
    // geadded werden, unabhängig von der konfigurierten Reihenfolge (Order). Dieses Problem müsste
    // auch gelöst sein, wenn alles nur noch IActions sind.
    Collections.reverse(result);
    return result;
  }

  @Override
  protected void attachModel() {
    super.attachModel();
    if (m_desktopListener != null) {
      throw new IllegalStateException();
    }
    m_desktopListener = new P_DesktopListener();
    getModel().addDesktopListener(m_desktopListener);
  }

  @Override
  protected void detachModel() {
    super.detachModel();
    if (m_desktopListener == null) {
      throw new IllegalStateException();
    }
    getModel().removeDesktopListener(m_desktopListener);
    m_desktopListener = null;
  }

  @Override
  protected void initJsonProperties(T model) {
    super.initJsonProperties(model);
    putJsonProperty(new JsonProperty<T>(IDesktop.PROP_TITLE, model) {
      @Override
      protected String modelValue() {
        return getModel().getTitle();
      }
    });
    putJsonProperty(new JsonProperty<T>(IDesktop.PROP_AUTO_TAB_KEY_STROKES_ENABLED, model) {
      @Override
      protected Object modelValue() {
        return getModel().isAutoTabKeyStrokesEnabled();
      }
    });
    putJsonProperty(new JsonProperty<T>(IDesktop.PROP_AUTO_TAB_KEY_STROKE_MODIFIER, model) {
      @Override
      protected Object modelValue() {
        return getModel().getAutoTabKeyStrokeModifier();
      }
    });
  }

  @Override
  public JSONObject toJson() {
    JSONObject json = super.toJson();
    putAdapterIdsProperty(json, "views", getViews());
    putAdapterIdsProperty(json, "dialogs", getDialogs());
    putAdapterIdsProperty(json, "messageBoxes", getModel().getMessageBoxStack());
    putAdapterIdsProperty(json, "actions", filterModelActions(), new DisplayableActionFilter<IAction>());
    putAdapterIdsProperty(json, "addOns", getModel().getAddOns());
    putAdapterIdsProperty(json, "keyStrokes", getModel().getKeyStrokes(), new DisplayableActionFilter<IKeyStroke>());
    if (!isFormBased()) {
      // FIXME CGU: view and tool buttons should be removed from desktop by device transformer
      putAdapterIdsProperty(json, "viewButtons", getModel().getViewButtons(), new DisplayableActionFilter<IViewButton>());
      putAdapterIdProperty(json, "outline", getModel().getOutline(), new DisplayableOutlineFilter<IOutline>());
      putAdapterIdProperty(json, "searchOutline", getSearchOutline(), new DisplayableOutlineFilter<IOutline>());
    }
    return json;
  }

  protected boolean isFormBased() {
    return false;
  }

  protected List<IForm> getViews() {
    List<IForm> views = new ArrayList<>();
    for (IForm form : getModel().getViewStack()) {
      if (!isFormBlocked(form)) {
        views.add(form);
      }
    }
    return views;
  }

  protected List<IForm> getDialogs() {
    List<IForm> dialogs = new ArrayList<>();
    for (IForm form : getModel().getDialogStack()) {
      if (!isFormBlocked(form)) {
        dialogs.add(form);
      }
    }
    return dialogs;
  }

  protected boolean isFormBlocked(IForm form) {
    // FIXME CGU: ignore desktop forms for the moment, should not be done here, application should handle it or abstractDesktop
    return (!isFormBased() && (form instanceof IOutlineTableForm || form instanceof IOutlineTreeForm));
  }

  protected void handleModelDesktopEvent(DesktopEvent event) {
    switch (event.getType()) {
      case DesktopEvent.TYPE_OUTLINE_CHANGED:
        handleModelOutlineChanged(event.getOutline());
        break;
      case DesktopEvent.TYPE_FORM_ADDED:
        handleModelFormAdded(event.getForm());
        break;
      case DesktopEvent.TYPE_FORM_REMOVED:
        handleModelFormRemoved(event.getForm());
        break;
      case DesktopEvent.TYPE_FORM_ENSURE_VISIBLE:
        handleModelFormEnsureVisible(event.getForm());
        break;
      case DesktopEvent.TYPE_OPEN_URI:
        handleModelOpenUri(event.getUri(), event.getTarget());
        break;
      case DesktopEvent.TYPE_DOWNLOAD_RESOURCE:
        handleModelDownloadResource(event.getDownloadHandler());
        break;
      case DesktopEvent.TYPE_MESSAGE_BOX_ADDED:
        handleModelMessageBoxAdded(event.getMessageBox());
        break;
      case DesktopEvent.TYPE_DESKTOP_CLOSED:
        handleModelDesktopClosed();
        break;
      default:
        // NOP
    }
  }

  protected void handleModelOpenUri(String uri, ITargetWindow target) {
    // Note: property 'target' is reserved by Scouts JSON protocol
    JSONObject json = new JSONObject();
    putProperty(json, "uri", uri);
    putProperty(json, "uriTarget", target.getIdentifier());
    addActionEvent("openUri", json);
  }

  /**
   * Target for download URL is AUTO because it depends on the type of browser if the browser opens a new window for the
   * downloaded resource or if the download is processed in the same window as the Scout application.
   */
  protected void handleModelDownloadResource(IDownloadHandler handler) {
    if (handler == null) {
      return; // FIXME AWE: (download) remove this null if?
    }
    manageDownloadHandlers();
    String fileName = handler.getResource().getFilename();
    m_downloadHandlers.put(fileName, handler);
    String downloadUrl = BinaryResourceUrlUtility.createDynamicAdapterResourceUrl(this, fileName);
    handleModelOpenUri(downloadUrl, TargetWindow.AUTO); // FIXME AWE: (download) unit-test AUTO
  }

  @Override
  public BinaryResource loadDynamicResource(String fileName) {
    manageDownloadHandlers();
    IDownloadHandler handler = m_downloadHandlers.get(fileName);
    if (handler != null) {
      BinaryResource binaryResource = handler.getResource();
      if (binaryResource != null) {
        return binaryResource;
      }
    }
    return null;
  }

  protected void manageDownloadHandlers() {
    for (Iterator<IDownloadHandler> it = m_downloadHandlers.values().iterator(); it.hasNext();) {
      IDownloadHandler handler = it.next();
      if (!handler.isActive()) {
        it.remove();
      }
    }
  }

  protected void handleModelOutlineChanged(IOutline outline) {
    if (isFormBased()) {
      return;
    }
    JSONObject jsonEvent = new JSONObject();
    IJsonAdapter<?> jsonAdapter = attachGlobalAdapter(outline);
    putProperty(jsonEvent, PROP_OUTLINE, jsonAdapter.getId());
    addActionEvent("outlineChanged", jsonEvent);
  }

  protected void handleModelFormAdded(IForm form) {
    if (isFormBlocked(form)) {
      return;
    }
    JSONObject jsonEvent = new JSONObject();
    IJsonAdapter<?> jsonAdapter = attachGlobalAdapter(form);
    putProperty(jsonEvent, PROP_FORM, jsonAdapter.getId());
    addActionEvent("formAdded", jsonEvent);
  }

  protected void handleModelFormRemoved(IForm form) {
    IJsonAdapter<?> jsonAdapter = getAdapter(form);
    // When the form is closed, it is disposed automatically. Therefore, we cannot resolve
    // the adapter anymore, hence the null check. (This is no problem, because 'formClosed'
    // event implies 'formRemoved', see Form.js/_onFormClosed.)
    if (jsonAdapter != null) {
      JSONObject jsonEvent = new JSONObject();
      putProperty(jsonEvent, PROP_FORM, jsonAdapter.getId());
      addActionEvent("formRemoved", jsonEvent);
    }
  }

  protected void handleModelFormEnsureVisible(IForm form) {
    JSONObject jsonEvent = new JSONObject();
    IJsonAdapter<?> jsonAdapter = getAdapter(form);
    putProperty(jsonEvent, PROP_FORM, jsonAdapter.getId());
    addActionEvent("formEnsureVisible", jsonEvent);
  }

  protected void handleModelMessageBoxAdded(final IMessageBox messageBox) {
    JSONObject jsonEvent = new JSONObject();
    IJsonAdapter<?> jsonAdapter = attachGlobalAdapter(messageBox);
    putProperty(jsonEvent, PROP_MESSAGE_BOX, jsonAdapter.getId());
    addActionEvent("messageBoxAdded", jsonEvent);
  }

  protected void handleModelDesktopClosed() {
    getUiSession().logout();
  }

  protected ISearchOutline getSearchOutline() {
    for (IOutline outline : getModel().getAvailableOutlines()) {
      if (outline instanceof ISearchOutline) {
        return (ISearchOutline) outline;
      }
    }
    return null;
  }

  @Override
  public void handleUiEvent(JsonEvent event) {
    if ("outlineChanged".equals(event.getType())) {
      handleUiOutlineChanged(event);
    }
    else {
      super.handleUiEvent(event);
    }
  }

  protected void handleUiOutlineChanged(JsonEvent event) {
    String outlineId = JsonObjectUtility.getString(event.getData(), "outlineId");
    IJsonAdapter<?> jsonOutline = getUiSession().getJsonAdapter(outlineId);
    getModel().setOutline((IOutline) jsonOutline.getModel());
  }

  protected class P_DesktopListener implements DesktopListener {

    @Override
    public void desktopChanged(DesktopEvent e) {
      handleModelDesktopEvent(e);
    }
  }
}
